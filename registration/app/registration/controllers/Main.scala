package registration.controllers

import java.util.UUID

import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import cats.data.{Xor, XorT}
import cats.implicits._
import error.{NotificationsError, RequestError}
import models._
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import registration.models.{LegacyRegistration, LegacyTopic}
import registration.services.azure.UdidNotFound
import registration.services.{UnsupportedPlatform, _}
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import cats.implicits._
import providers.ProviderError

import scala.util.{Success, Try}

final class Main(
  registrarProvider: RegistrarProvider,
  topicValidator: TopicValidator,
  legacyClient: LegacyRegistrationClient,
  legacyRegistrationConverter: LegacyRegistrationConverter)
    (implicit executionContext: ExecutionContext)
  extends Controller {

  private val logger = Logger(classOf[Main])

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  def register(lastKnownDeviceId: String): Action[Registration] = Action.async(BodyJson[Registration]) { request =>
    registerCommon(lastKnownDeviceId, request.body).map(processResponse(_))
  }

  def unregister(platform: Platform, udid: UniqueDeviceIdentifier): Action[AnyContent] = Action.async {

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

  def legacyRegister: Action[LegacyRegistration] = Action.async(BodyJson[LegacyRegistration]) { request =>
    val legacyRegistration = request.body

    val result = for {
      registration <- XorT.fromXor[Future](legacyRegistrationConverter.toRegistration(legacyRegistration))
      registrationResponse <- XorT(registerCommon(registration.deviceId, registration))
    } yield {
      unregisterFromLegacy(legacyRegistration)
      legacyRegistrationConverter.fromResponse(legacyRegistration, registrationResponse)
    }

    result.value.map(processResponse(_))
  }

  private def unregisterFromLegacy(registration: LegacyRegistration) = legacyClient.unregister(registration).foreach {
    case Xor.Right(_) =>
      logger.debug(s"Unregistered ${registration.device.udid} from legacy notifications")
    case Xor.Left(error) =>
      logger.error(s"Failed to unregistered ${registration.device.udid} from legacy notifications: $error")
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
    result.fold(processErrors, res => Ok(Json.toJson(res)(writer)))

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

}
