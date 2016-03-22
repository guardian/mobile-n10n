package registration.controllers

import javax.inject.Inject

import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import error.NotificationsError
import models.{Topic, Registration}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{AnyContent, Action, Controller, Result}
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

  def register(lastKnownDeviceId: String): Action[Registration] = Action.async(BodyJson[Registration]) { request =>
    val registration = request.body

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
        .map { processResponse }

    registrarFor(registration) match {
      case \/-(registrar) =>
        validate(registration.topics).flatMap(registerWith(registrar, _))
      case -\/(msg) => Future.successful(InternalServerError(msg))
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
      case -\/(other) =>
        logger.error(s"Unknown error: ${other.reason}")
        InternalServerError(other.reason)
    }
  }

}
