package registration.controllers

import javax.inject.Inject

import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import models.Registration
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{AnyContent, Action, Controller, Result}
import providers.Error
import registration.services.{RegistrationResponse, NotificationRegistrar, RegistrarSupport}
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/, \/-}

final class Main @Inject()(notificationRegistrarSupport: RegistrarSupport, topicValidator: TopicValidator)
    (implicit executionContext: ExecutionContext)
  extends Controller {

  import notificationRegistrarSupport._

  private val logger = Logger(classOf[Main])

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  def register(oldDeviceId: String): Action[Registration] = Action.async(BodyJson[Registration]) { request =>
    val registration = request.body

    def registerWith(registrar: NotificationRegistrar): Future[Result] =
      topicValidator.removeInvalid(registration.topics) flatMap {
        case \/-(validTopics) => registrar
          .register(oldDeviceId, registration.copy(topics = validTopics))
          .map { processResponse }
        case -\/(e) => Future.successful(InternalServerError(e.reason))
      }

    registrarFor(registration) match {
      case -\/(msg) => Future.successful(InternalServerError(msg))
      case \/-(registrar) => registerWith(registrar)
    }
  }

  private def processResponse(result: Error \/ RegistrationResponse): Result = {
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
      case -\/(other) =>
        logger.error(s"Unknown error: ${other.reason}")
        InternalServerError(other.reason)
    }
  }

}
