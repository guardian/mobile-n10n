package registration.controllers

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.Source
import akka.util.ByteString
import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import binders.querystringbinders.{RegistrationsByDeviceToken, RegistrationsByTopicParams, RegistrationsByUdidParams, RegistrationsSelector}
import cats.data.EitherT
import cats.implicits._
import error.{NotificationsError, RequestError}
import models._
import play.api.Logger
import play.api.libs.json.{Format, Json, Writes}
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc._
import registration.models.{LegacyNewsstandRegistration, LegacyRegistration}
import registration.services._
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import models.pagination.{CursorSet, Paginated}
import play.api.http.HttpEntity
import providers.ProviderError

import scala.util.{Success, Try}

final class Main(
  registrar: NotificationRegistrar,
  topicValidator: TopicValidator,
  legacyRegistrationConverter: LegacyRegistrationConverter,
  legacyNewsstandRegistrationConverter: LegacyNewsstandRegistrationConverter,
  config: Configuration,
  controllerComponents: ControllerComponents
 )
  (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractController(controllerComponents) {

  private val logger = Logger(classOf[Main])

  def healthCheck: Action[AnyContent] = Action {
    // This forces Play to close the connection rather than allowing
    // keep-alive (because the content length is unknown)
    Ok.sendEntity(
      HttpEntity.Streamed(
        data =  Source(Array(ByteString("Good")).toVector),
        contentLength = None,
        contentType = Some("text/plain")
      )
    )
  }

  def unregister(selector: RegistrationsByDeviceToken): Action[AnyContent] = actionWithTimeout {
    registrar.unregister(selector.deviceToken, selector.platform).map {
      case Right(_) => NoContent
      case Left(error) => processErrors(error)
    }
  }

  def newsstandRegister: Action[LegacyNewsstandRegistration] =
    registerWithConverter(legacyNewsstandRegistrationConverter)

  def legacyRegister: Action[LegacyRegistration] =
    registerWithConverter(legacyRegistrationConverter)

  private def registerWithConverter[T](converter: RegistrationConverter[T])(implicit format: Format[T]): Action[T] = actionWithTimeout(parse.json[T]) { request =>
    val legacyRegistration = request.body
    val result = for {
      registration <- EitherT.fromEither[Future](converter.toRegistration(legacyRegistration))
      registrationResponse <- EitherT(registerCommon(registration))
    } yield {
      converter.fromResponse(legacyRegistration, registrationResponse)
    }

    result.value.map(processResponse(_))
  }

  def registrations(selector: RegistrationsSelector): Action[AnyContent] = {
    selector match {
      case v: RegistrationsByUdidParams => registrationsByUdid(v.udid)
      case v: RegistrationsByTopicParams => registrationsByTopic(v.topic, v.cursor)
      case v: RegistrationsByDeviceToken => registrationsByDeviceToken(v.platform, v.deviceToken)
    }
  }

  def registrationsByTopic(topic: Topic, cursors: Option[CursorSet]): Action[AnyContent] = Action.async {
    val isFirstPage = cursors.isEmpty

    val registrarResults = {
      val cursorForProvider = cursors.flatMap(_.providerCursor(registrar.providerIdentifier))

      if (isFirstPage || cursorForProvider.isDefined)
        registrar.findRegistrations(topic, cursorForProvider.map(_.cursor)).map(_.toOption)
      else
        Future.successful(None)
    }

    registrarResults.map { pageAttempts =>
      val results = pageAttempts.map(_.results).getOrElse(Nil)
      val cursor = pageAttempts.flatMap(_.cursor).map(c => CursorSet.apply(List(c)))

      Ok(Json.toJson(Paginated(results, cursor)))
    }
  }

  def registrationsByDeviceToken(platform: Platform, deviceToken: DeviceToken): Action[AnyContent] = Action.async {
    val result = for {
      registrations <- EitherT(registrar.findRegistrations(deviceToken, platform): Future[Either[NotificationsError, List[StoredRegistration]]])
    } yield registrations
    result.fold(processErrors, res => Ok(Json.toJson(res)))
  }

  def registrationsByUdid(udid: UniqueDeviceIdentifier): Action[AnyContent] = Action.async {
    registrar.findRegistrations(udid).map {
      case Right(Paginated(responses, _)) => Ok(Json.toJson(responses))
      case Left(error) => processErrors(error)
    }
  }

  private def registerCommon(registration: Registration): Future[Either[NotificationsError, RegistrationResponse]] = {

    def validate(topics: Set[Topic]): Future[Set[Topic]] =
      topicValidator
        .removeInvalid(topics)
        .map {
          case Right(filteredTopics) =>
            logger.debug(s"Successfully validated topics in registration (${registration.deviceToken}), topics valid: [$filteredTopics]")
            filteredTopics
          case Left(e) =>
            logger.error(s"Could not validate topics ${e.topicsQueried} for registration (${registration.deviceToken}), reason: ${e.reason}")
            topics
        }

    def registerWith(registrar: NotificationRegistrar, topics: Set[Topic]) =
      registrar
        .register(registration.deviceToken, registration.copy(topics = topics))

    def logErrors: PartialFunction[Try[Either[ProviderError, RegistrationResponse]], Unit] = {
      case Success(Left(v)) => logger.error(s"Failed to register $registration with ${v.providerName}: ${v.reason}")
    }

    validate(registration.topics).flatMap(registerWith(registrar, _).andThen(logErrors))
  }

  private def processResponse[T](result: Either[NotificationsError, T])(implicit writer: Writes[T]) : Result =
    result.fold(processErrors, res => Ok(Json.toJson(res)))

  private def processErrors(error: NotificationsError): Result = error match {
    case HubServiceError(reason, code) =>
      logger.error(s"Service error code $code: $reason")
      Status(code.toInt)(s"Upstream service failed with code $code.")
    case HubParseFailed(body, reason) =>
      logger.error(s"Failed to parse body due to: $reason; body = $body")
      InternalServerError(reason)
    case HubInvalidConnectionString(reason) =>
      logger.error(s"Failed due to invalid connection string: $reason")
      InternalServerError(reason)
    case requestError: RequestError =>
      logger.warn(s"Bad request: ${requestError.reason}")
      BadRequest(requestError.reason)
    case other =>
      logger.error(s"Unknown error: ${other.reason}")
      InternalServerError(other.reason)
  }

  private def actionWithTimeout[T](bodyParser: BodyParser[T])(block: Request[T] => Future[Result]): Action[T] = {
    Action.async(bodyParser) { request =>
      val timeoutFuture = after(config.defaultTimeout, using = system.scheduler)(Future.successful(InternalServerError("Operation timed out")))
      Future.firstCompletedOf(Seq(block(request), timeoutFuture))
    }
  }

  private def actionWithTimeout(block: => Future[Result]): Action[AnyContent] =
    actionWithTimeout(parse.ignore(AnyContentAsEmpty: AnyContent))(_ => block)

}
