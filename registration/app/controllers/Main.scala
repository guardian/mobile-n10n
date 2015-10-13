package controllers

import javax.inject.Inject

import gu.msnotifications.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import models.{ApiResponse, Registration}
import notifications.providers
import notifications.providers.{RegistrationResponse, NotificationRegistrar}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, Controller, Result}
import services._
import services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/, \/-}

final class Main @Inject()(notificationRegistrarSupport: RegistrarSupport, topicValidator: TopicValidator)
    (implicit executionContext: ExecutionContext)
  extends Controller {

  import notificationRegistrarSupport._

  private val logger = Logger("main")

  def healthCheck = Action {
    Ok("Good")
  }

  def register(deviceId: String) = Action.async(BodyJson[Registration]) { request =>
    val registration = request.body.copy(deviceId = deviceId)

    def registerWith(registrar: NotificationRegistrar): Future[Result] =
      topicValidator.removeInvalid(registration.topics) flatMap {
        case \/-(validTopics) => registrar
          .register(registration.copy(topics = validTopics))
          .map { processResponse }
        case -\/(e) => Future.successful(InternalServerError(e.reason))
      }

    registrarFor(registration) match {
      case -\/(msg) => Future.successful(InternalServerError(msg))
      case \/-(registrar) => registerWith(registrar)
    }
  }

  private def processResponse(result: providers.Error \/ RegistrationResponse): Result = {
    result match {
      case \/-(res) =>
        Ok(Json.toJson(res))
      case -\/(HubServiceError(reason, code)) =>
        logger.error(message = s"Service error code $code: $reason")
        Status(code.toInt)(s"Upstream service failed with code $code.")
      case -\/(HubParseFailed(body, reason)) =>
        logger.error(message = s"Failed to parse body due to: $reason; body = $body")
        InternalServerError(reason)
      case -\/(HubInvalidConnectionString(reason)) =>
        logger.error(message = s"Failed due to invalid connection string: $reason")
        InternalServerError(reason)
      case -\/(other) =>
        logger.error(message = s"Unknown error: ${other.reason}")
        InternalServerError(other.reason)
    }
  }

}