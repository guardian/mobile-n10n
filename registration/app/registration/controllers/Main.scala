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
import scalaz.{-\/, \/, \/-}
import scalaz.syntax.either._

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
      case \/-(registration) =>
        val registrationResult = registerCommon(registration.deviceId, registration)
        registrationResult onSuccess {
          case \/-(_) => unregisterFromLegacy(request.body)
        }
        registrationResult
      case -\/(error) =>
        Future.successful(error.left)
    }
    result.map(processResponse)
  }

  private def unregisterFromLegacy(registration: LegacyRegistration) = legacyClient.unregister(registration).foreach {
    case \/-(_) =>
      logger.debug(s"Unregistered ${registration.device.udid} from legacy notifications")
    case -\/(error) =>
      logger.error(s"Failed to unregistered ${registration.device.udid} from legacy notifications: $error")
  }

  private def registerCommon(lastKnownDeviceId: String, registration: Registration): Future[NotificationsError \/ RegistrationResponse] = {

    def validate(topics: Set[Topic]): Future[Set[Topic]] =
      topicValidator
        .removeInvalid(topics)
        .map {
          case \/-(filteredTopics) =>
            logger.debug(s"Successfully validated topics in registration (${registration.deviceId}), topics valid: [$filteredTopics]")
            filteredTopics
          case -\/(e) =>
            logger.error(s"Could not validate topics ${e.topicsQueried} for registration (${registration.deviceId}), reason: ${e.reason}")
            topics
        }

    def registerWith(registrar: NotificationRegistrar, topics: Set[Topic]) =
      registrar
        .register(lastKnownDeviceId, registration.copy(topics = topics))

    registrarProvider.registrarFor(registration) match {
      case \/-(registrar) =>
        validate(registration.topics).flatMap(registerWith(registrar, _))
      case -\/(error) =>
        Future.successful(error.left)
    }
  }

  private def processResponse(result: NotificationsError \/ RegistrationResponse): Result = {
    result match {
      case \/-(res) =>
        Ok(Json.toJson(res))
      case -\/(HubServiceError(reason, code)) =>
        logger.error(s"Service error code $code: $reason")
        Status(code.toInt)(s"Upstream service failed with code $code.")
      case -\/(HubParseFailed(body, reason)) =>
        logger.error(s"Failed to parse body due to: $reason; body = $body")
        InternalServerError(reason)
      case -\/(HubInvalidConnectionString(reason)) =>
        logger.error(s"Failed due to invalid connection string: $reason")
        InternalServerError(reason)
      case -\/(requestError: RequestError) =>
        logger.warn(s"Bad request: ${requestError.reason}")
        BadRequest(requestError.reason)
      case -\/(other) =>
        logger.error(s"Unknown error: ${other.reason}")
        InternalServerError(other.reason)
    }
  }

}
