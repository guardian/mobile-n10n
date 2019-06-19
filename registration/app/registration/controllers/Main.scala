package registration.controllers

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import error.{NotificationsError, RequestError}
import models._
import play.api.Logger
import play.api.libs.json.{Format, Json, Writes}
import play.api.mvc._
import registration.models.{LegacyNewsstandRegistration, LegacyRegistration}
import registration.services._
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
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
