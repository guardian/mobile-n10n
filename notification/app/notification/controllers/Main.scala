package notification.controllers

import java.util.UUID

import authentication.AuthenticationSupport
import error.NotificationsError
import models._
import notification.models.{Push, PushResult}
import notification.services.{Configuration, NotificationSender}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import tracking.SentNotificationReportRepository

import scala.concurrent.Future.sequence
import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import models.TopicTypes.ElectionResults

final class Main(
  configuration: Configuration,
  senders: List[NotificationSender],
  notificationReportRepository: SentNotificationReportRepository
)(implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  val logger = Logger(classOf[Main])

  val weekendReadingTopic = Topic(TopicTypes.TagSeries, "membership/series/weekend-reading")
  val weekendRoundUpTopic = Topic(TopicTypes.TagSeries, "membership/series/weekend-round-up")
  
  override def validApiKey(apiKey: String): Boolean = configuration.apiKeys.contains(apiKey) || configuration.electionRestrictedApiKeys.contains(apiKey)

  override def isPermittedTopic(apiKey: String): Topic => Boolean = {
    if (configuration.electionRestrictedApiKeys.contains(apiKey)) {
      topic => topic.`type` == ElectionResults
    } else {
      _ => true
    }
  }

  def handleErrors[T](result: T): Result = result match {
    case error: NotificationsError => InternalServerError(error.reason)
  }

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  @deprecated("A push notification can be sent to multiple topics, this is for backward compatibility only", since = "07/12/2015")
  def pushTopic(topic: Topic): Action[Notification] = pushTopics

  def pushTopics: Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    // todo: remove once client-side migrates users from weekend-round-up to weekend-reading
    val topics = {
      val rawTopics = request.body.topic

      if (rawTopics.contains(weekendReadingTopic)) {
        rawTopics + weekendRoundUpTopic
      } else {
        rawTopics
      }
    }

    val MaxTopics = 20
    topics.size match {
      case 0 => Future.successful(BadRequest("Empty topic list"))
      case a: Int if a > MaxTopics => Future.successful(BadRequest(s"Too many topics, maximum: $MaxTopics"))
      case _ if !topics.forall(request.isPermittedTopic) => Future.successful(Unauthorized(s"This API key is not valid for ${topics.filterNot(request.isPermittedTopic)}."))
      case _ => pushWithDuplicateProtection(Push(request.body.withTopics(topics), Left(topics)))
    }
  }

  def pushUser(userId: UUID): Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Right(UniqueDeviceIdentifier(userId)))
    pushWithDuplicateProtection(push)
  }

  private def pushWithDuplicateProtection(push: Push): Future[Result] = {
    val isDuplicate = notificationReportRepository.getByUuid(push.notification.id).map(_.isRight)

    isDuplicate.flatMap {
      case true => Future.successful(BadRequest(s"${push.notification.id} has been sent before - refusing to resend"))
      case false => pushGeneric(push)
    }
  }

  private def pushGeneric(push: Push) = {
    sendNotifications(push, to = senders) flatMap {
      case (Nil, reports @ _ :: _) =>
        reportPushSent(push.notification, reports) map {
          case Xor.Right(_) =>
            logger.info(s"Notification was sent: $push")
            Created(toJson(PushResult(push.notification.id)))
          case Xor.Left(error) =>
            logger.error(s"Notification ($push) sent but report could not be stored ($error)")
            Created(toJson(PushResult(push.notification.id).withReportingError(error)))
        }
      case (rejected @ _ :: _, reports @ _ :: _) =>
        reportPushSent(push.notification, reports) map {
          case Xor.Right(_) =>
            logger.warn(s"Notification ($push) was rejected by some providers: ($rejected)")
            Created(toJson(PushResult(push.notification.id).withRejected(rejected)))
          case Xor.Left(error) =>
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

