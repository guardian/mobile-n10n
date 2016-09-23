package registration.controllers

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.Source
import akka.util.ByteString
import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import cats.data.XorT
import cats.implicits._
import error.{NotificationsError, RequestError}
import models._
import play.api.Logger
import play.api.libs.json.{Json, Reads}
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, BodyParser, BodyParsers, Controller, Request, Result}
import registration.models.{LegacyNewsstandRegistration, LegacyRegistration}
import registration.services.azure.UdidNotFound
import registration.services._
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import cats.implicits._
import play.api.http.HttpEntity
import providers.ProviderError

import scala.concurrent.duration._
import scala.util.{Success, Try}

final class Main(
  registrarProvider: RegistrarProvider,
  topicValidator: TopicValidator,
  legacyClient: LegacyRegistrationClient,
  legacyRegistrationConverter: LegacyRegistrationConverter,
  legacyNewsstandRegistrationConverter: LegacyNewsstandRegistrationConverter,
  config: Configuration)
  (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends Controller {

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

  def register(lastKnownDeviceId: String): Action[Registration] = actionWithTimeout(BodyJson[Registration]) { request =>
    registerCommon(lastKnownDeviceId, request.body).map(processResponse)
  }

  def unregister(platform: Platform, udid: UniqueDeviceIdentifier): Action[AnyContent] = actionWithTimeout {

    def registrarFor(platform: Platform) = XorT.fromXor[Future](
      registrarProvider.registrarFor(platform)
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

  private def registerWithConverter[T](converter: RegistrationConverter[T])(implicit reader: Reads[T]): Action[T] = actionWithTimeout(BodyJson[T]) { request =>
    val result = converter.toRegistration(request.body) match {
      case Xor.Right(registration) =>
        val registrationResult = registerCommon(registration.deviceId, registration)
        registrationResult onSuccess {
          case Xor.Right(_) => unregisterFromLegacy(registration)
        }
        registrationResult
      case Xor.Left(error) =>
        Future.successful(Xor.left(error))
    }
    result.map(processResponse)
  }

  private def unregisterFromLegacy(registration: Registration) = legacyClient.unregister(registration.udid).foreach {
    case Xor.Right(_) =>
      logger.debug(s"Unregistered ${registration.udid} from legacy notifications")
    case Xor.Left(error) =>
      logger.error(s"Failed to unregistered ${registration.udid} from legacy notifications: $error")
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

  private def processResponse(result: NotificationsError Xor RegistrationResponse): Result =
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
    actionWithTimeout(BodyParsers.parse.ignore(AnyContentAsEmpty: AnyContent))(_ => block)

}
