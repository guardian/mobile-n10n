package notification.controllers

import java.util.UUID

import authentication.AuthenticationSupport
import error.NotificationsError
import models._
import notification.models.{PushResult, Push}
import notification.services.{NotificationSender, Configuration}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import tracking.SentNotificationReportRepository

import scala.concurrent.Future.sequence
import scala.concurrent.{ExecutionContext, Future}
import scalaz.{\/-, -\/}

final class Main(
  configuration: Configuration,
  senders: List[NotificationSender],
  notificationReportRepository: SentNotificationReportRepository
)(implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  val logger = Logger(classOf[Main])

  override def validApiKey(apiKey: String): Boolean = configuration.apiKeys.contains(apiKey)

  def handleErrors[T](result: T): Result = result match {
    case error: NotificationsError => InternalServerError(error.reason)
  }

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  @deprecated("A push notification can be sent to multiple topics, this is for backward compatibility only", since = "07/12/2015")
  def pushTopic(topic: Topic): Action[Notification] = pushTopics

  def pushTopics: Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val topics = request.body.topic
    val MaxTopics = 20
    topics.size match {
      case 0 => Future.successful(BadRequest("Empty topic list"))
      case a: Int if a > MaxTopics => Future.successful(BadRequest(s"Too many topics, maximum: $MaxTopics"))
      case _ => pushGeneric(Push(request.body, Left(topics)))
    }
  }

  def pushUser(userId: UUID): Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Right(UserId(userId)))
    pushGeneric(push)
  }

  private def pushGeneric(push: Push) = {
    sendNotifications(push, to = senders) flatMap {
      case (Nil, reports @ _ :: _) =>
        reportPushSent(push.notification, reports) map {
          case \/-(_) =>
            logger.info(s"Notification was sent: $push")
            Created(toJson(PushResult(push.notification.id)))
          case -\/(error) =>
            logger.error(s"Notification ($push) sent but report could not be stored ($error)")
            Created(toJson(PushResult(push.notification.id).withReportingError(error)))
        }
      case (rejected @ _ :: _, reports @ _ :: _) =>
        reportPushSent(push.notification, reports) map {
          case \/-(_) =>
            logger.warn(s"Notification ($push) was rejected by some providers: ($rejected)")
            Created(toJson(PushResult(push.notification.id).withRejected(rejected)))
          case -\/(error) =>
            logger.error(s"Notification ($push) was rejected by some providers and there was error in reporting")
            Created(toJson(PushResult(push.notification.id).withRejected(rejected).withReportingError(error)))
        }
      case (allRejected @ _ :: _, Nil) =>
        logger.error(s"Notification ($push) could not be sent: $allRejected")
        Future.successful(InternalServerError)
      case _ =>
        Future.successful(NotFound)
    }
  }

  private def sendNotifications(push: Push, to: List[NotificationSender]) = {
    val sendResults = senders.map { _.sendNotification(push) }
    sequence(sendResults) map { results =>
      val rejected = results.flatMap(_.swap.toOption)
      val reports = results.flatMap(_.toOption)
      (rejected, reports)
    }
  }

  private def reportPushSent(notification: Notification, reports: List[SenderReport]) =
    notificationReportRepository.store(NotificationReport.create(notification, reports))
}

