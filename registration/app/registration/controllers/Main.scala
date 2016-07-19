package registration.controllers

import java.util.UUID

import azure.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import error.NotificationsError
import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import registration.models.LegacyRegistration
import registration.services.{NotificationRegistrar, RegistrarProvider, RegistrationResponse}
import registration.services.topic.TopicValidator

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/, \/-}

final class Main(registrarProvider: RegistrarProvider, topicValidator: TopicValidator)
    (implicit executionContext: ExecutionContext)
  extends Controller {

  private val logger = Logger(classOf[Main])

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  def register(lastKnownDeviceId: String): Action[Registration] = Action.async(BodyJson[Registration]) { request =>
    registerCommon(lastKnownDeviceId, request.body)
  }

  def legacyRegister: Action[LegacyRegistration] = Action.async(BodyJson[LegacyRegistration]) { request =>
    val topics = for {
      topics <- request.body.preferences.topics.toList
      topic <- topics
      topicType <- TopicType.fromString(topic.`type`)
    } yield Topic(topicType, topic.name) // todo: check this

    val matchTopics = for {
      topics <- request.body.preferences.matches.toList
      topic <- topics
    } yield Topic(TopicTypes.FootballMatch, topic.matchId) // todo: check this

    val breakingTopic = if (request.body.preferences.receiveNewsAlerts)
      Some(Topic(TopicTypes.Breaking, request.body.preferences.edition)) // todo: check this
    else None

    val result = for {
      platform <- Platform.fromString(request.body.device.platform)
    } yield {
      val registration = Registration(
        deviceId = request.body.device.pushToken,
        platform = platform,
        userId = UserId(UUID.fromString(request.body.device.udid)),
        topics = (topics ++ matchTopics ++ breakingTopic.toList).toSet
      )
      registerCommon(registration.deviceId, registration)
    }
    result getOrElse Future.successful(InternalServerError("Platform not recognised"))
  }

  private def registerCommon(lastKnownDeviceId: String, registration: Registration): Future[Result] = {

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

    registrarProvider.registrarFor(registration) match {
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
