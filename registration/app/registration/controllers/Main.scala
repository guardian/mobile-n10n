package registration.controllers

import java.util.UUID

import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import error.{NotificationsError, RequestError}
import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import registration.models.LegacyRegistration
import registration.services.{UnsupportedPlatform, _}
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import cats.implicits._

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
    registerCommon(lastKnownDeviceId, request.body).map(processResponse)
  }

  def legacyRegister: Action[LegacyRegistration] = Action.async(BodyJson[LegacyRegistration]) { request =>
    val result = legacyRegistrationConverter.toRegistration(request.body) match {
      case Xor.Right(registration) =>
        val registrationResult = registerCommon(registration.deviceId, registration)
        registrationResult onSuccess {
          case Xor.Right(_) => unregisterFromLegacy(request.body)
        }
        registrationResult
      case Xor.Left(error) =>
        Future.successful(Xor.left(error))
    }
    result.map(processResponse)
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

    registrarProvider.registrarFor(registration) match {
      case Xor.Right(registrar) =>
        validate(registration.topics).flatMap(registerWith(registrar, _))
      case Xor.Left(error) =>
        Future.successful(error.left)
    }
  }

  private def processResponse(result: NotificationsError Xor RegistrationResponse): Result = {
    result match {
      case Xor.Right(res) =>
        Ok(Json.toJson(res))
      case Xor.Left(HubServiceError(reason, code)) =>
        logger.error(s"Service error code $code: $reason")
        Status(code.toInt)(s"Upstream service failed with code $code.")
      case Xor.Left(HubParseFailed(body, reason)) =>
        logger.error(s"Failed to parse body due to: $reason; body = $body")
        InternalServerError(reason)
      case Xor.Left(HubInvalidConnectionString(reason)) =>
        logger.error(s"Failed due to invalid connection string: $reason")
        InternalServerError(reason)
      case Xor.Left(requestError: RequestError) =>
        logger.warn(s"Bad request: ${requestError.reason}")
        BadRequest(requestError.reason)
      case Xor.Left(other) =>
        logger.error(s"Unknown error: ${other.reason}")
        InternalServerError(other.reason)
    }
  }

}
