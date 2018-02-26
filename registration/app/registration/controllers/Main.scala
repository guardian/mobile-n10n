package registration.controllers

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.Source
import akka.util.ByteString
import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import binders.querystringbinders.{RegistrationsByDeviceToken, RegistrationsByTopicParams, RegistrationsByUdidParams, RegistrationsSelector}
import cats.data.XorT
import cats.implicits._
import error.{NotificationsError, RequestError}
import models._
import play.api.Logger
import play.api.libs.json.{Format, Json, Writes}
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc._
import registration.models.{LegacyNewsstandRegistration, LegacyRegistration}
import registration.services.azure.UdidNotFound
import registration.services._
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import cats.implicits._
import models.pagination.{CursorSet, Paginated}
import play.api.http.HttpEntity
import providers.ProviderError

import scala.util.{Success, Try}

final class Main(
  registrarProvider: RegistrarProvider,
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

  def register(lastKnownDeviceId: String): Action[Registration] = actionWithTimeout(parse.json[Registration]) { request =>
    registerCommon(lastKnownDeviceId, request.body).map(processResponse(_))
  }

  def unregister(platform: Platform, udid: UniqueDeviceIdentifier): Action[AnyContent] = actionWithTimeout {

    def registrarFor(platform: Platform) = XorT.fromXor[Future](
      registrarProvider.registrarFor(platform, None)
    )

    def unregisterFrom(registrar: NotificationRegistrar) = XorT(
      registrar.unregister(udid): Future[Xor[NotificationsError, Unit]]
    )

    registrarFor(platform)
      .flatMap(unregisterFrom)
      .fold(processErrors, _ => NoContent)
  }

  def newsstandRegister: Action[LegacyNewsstandRegistration] =
    registerWithConverter(legacyNewsstandRegistrationConverter)

  def legacyRegister: Action[LegacyRegistration] =
    registerWithConverter(legacyRegistrationConverter)

  private def registerWithConverter[T](converter: RegistrationConverter[T])(implicit format: Format[T]): Action[T] = actionWithTimeout(parse.json[T]) { request =>
    val legacyRegistration = request.body
    val result = for {
      registration <- XorT.fromXor[Future](converter.toRegistration(legacyRegistration))
      registrationResponse <- XorT(registerCommon(registration.deviceId, registration))
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

    val registrarResults = registrarProvider.withAllRegistrars { registrar =>
      val cursorForProvider = cursors.flatMap(_.providerCursor(registrar.providerIdentifier))

      if (isFirstPage || cursorForProvider.isDefined)
        registrar.findRegistrations(topic, cursorForProvider.map(_.cursor)).map(_.toOption)
      else
        Future.successful(None)
    }
    Future.sequence(registrarResults).map { pageAttempts =>
      val pages = pageAttempts.flatten

      val results = pages.flatMap(_.results)

      val cursor =
        Some(pages.flatMap(_.cursor))
          .filter(_.nonEmpty)
          .map(CursorSet.apply)

      Ok(Json.toJson(Paginated(results, cursor)))
    }
  }

  def registrationsByDeviceToken(platform: Platform, deviceToken: String): Action[AnyContent] = Action.async {
    val result = for {
      registrar <- XorT.fromXor[Future](registrarProvider.registrarFor(platform, None))
      registrations <- XorT(registrar.findRegistrations(deviceToken): Future[Xor[NotificationsError, List[StoredRegistration]]])
    } yield registrations
    result.fold(processErrors, res => Ok(Json.toJson(res)))
  }

  def registrationsByUdid(udid: UniqueDeviceIdentifier): Action[AnyContent] = Action.async {
    Future.sequence {
      registrarProvider.withAllRegistrars { registrar =>
        registrar.findRegistrations(udid)
      }
    } map { responses =>
      val allResults = for {
        response <- responses
        successfulResponse <- response.toList
        result <- successfulResponse.results
      } yield result
      Ok(Json.toJson(allResults))
    }
  }

  private def registerCommon(lastKnownDeviceId: String, registration: Registration): Future[NotificationsError Xor RegistrationResponse] = {

    def validate(topics: Set[Topic]): Future[Set[Topic]] =
      topicValidator
        .removeInvalid(topics)
        .map {
          case Xor.Right(filteredTopics) =>
            logger.debug(s"Successfully validated topics in registration (${registration.deviceId}), topics valid: [$filteredTopics]")
            filteredTopics
          case Xor.Left(e) =>
            logger.error(s"Could not validate topics ${e.topicsQueried} for registration (${registration.deviceId}), reason: ${e.reason}")
            topics
        }

    def registerWith(registrar: NotificationRegistrar, topics: Set[Topic]) =
      registrar
        .register(lastKnownDeviceId, registration.copy(topics = topics))

    def logErrors: PartialFunction[Try[ProviderError Xor RegistrationResponse], Unit] = {
      case Success(Xor.Left(v)) => logger.error(s"Failed to register $registration with ${v.providerName}: ${v.reason}")
    }

    registrarProvider.registrarFor(registration) match {
      case Xor.Right(registrar) =>
        validate(registration.topics).flatMap(registerWith(registrar, _).andThen(logErrors))
      case Xor.Left(error) =>
        Future.successful(error.left)
    }
  }

  private def processResponse[T](result: NotificationsError Xor T)(implicit writer: Writes[T]) : Result =
    result.fold(processErrors, res => Ok(Json.toJson(res)))

  private def processErrors(error: NotificationsError): Result = error match {
    case UdidNotFound =>
      NotFound("Udid not found")
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
