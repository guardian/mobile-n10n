package notification.controllers

import java.util.UUID

import authentication.AuthAction
import models._
import notification.models.{Push, PushResult}
import notification.services
import notification.services.azure.NewsstandSender
import notification.services.{Configuration, NotificationSender}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc._
import tracking.Repository.RepositoryResult
import tracking.SentNotificationReportRepository

import scala.concurrent.Future.sequence
import scala.concurrent.{ExecutionContext, Future}

final class Main(
  configuration: Configuration,
  senders: List[NotificationSender],
  newsstandSender: NewsstandSender,
  notificationReportRepository: SentNotificationReportRepository,
  controllerComponents: ControllerComponents,
  authAction: AuthAction
)(implicit executionContext: ExecutionContext)
  extends AbstractController(controllerComponents) {

  val logger = Logger(classOf[Main])
  val weekendReadingTopic = Topic(TopicTypes.TagSeries, "membership/series/weekend-reading")
  val weekendRoundUpTopic = Topic(TopicTypes.TagSeries, "membership/series/weekend-round-up")

  def healthCheck = Action {
    Ok("Good")
  }

  def pushNewsstand: Action[AnyContent] = authAction.async {
    val id = UUID.randomUUID()
    newsstandSender.sendNotification(id) map {
      case Right(_) =>
        logger.info("Newsstand notification sent")
        Created(toJson(PushResult(id)))
      case Left(error) =>
        logger.error(s"Newsstand notification failed: $error")
        InternalServerError(s"Newsstand notification failed: $error")
    }
  }

  @deprecated("A push notification can be sent to multiple topics, this is for backward compatibility only", since = "07/12/2015")
  def pushTopic(topic: Topic): Action[Notification] = pushTopics

  def pushTopics: Action[Notification] = authAction.async(parse.json[Notification]) { request =>
    val topics = request.body.topic
    val MaxTopics = 3
    topics.size match {
      case 0 => Future.successful(BadRequest("Empty topic list"))
      case a: Int if a > MaxTopics => Future.successful(BadRequest(s"Too many topics, maximum: $MaxTopics"))
      case _ if !topics.forall{request.isPermittedTopic} =>
        Future.successful(Unauthorized(s"This API key is not valid for ${topics.filterNot(request.isPermittedTopic)}."))
      case _ => pushWithDuplicateProtection(Push(request.body.withTopics(topics), topics.toSet))
    }
  }

  private def pushWithDuplicateProtection(push: Push): Future[Result] = {
    val isDuplicate = notificationReportRepository.getByUuid(push.notification.id).map(_.isRight)

    isDuplicate.flatMap {
      case true => Future.successful(BadRequest(s"${push.notification.id} has been sent before - refusing to resend"))
      case false => pushGeneric(push)
    }
  }

  private def pushGeneric(push: Push) = {
    prepareReportAndSendPush(push) flatMap {
      case (Nil, reports@_ :: _) =>
        reportPushSent(push.notification, reports) map {
          case Right(_) =>
            logger.info(s"Notification was sent: $push")
            Created(toJson(PushResult(push.notification.id)))
          case Left(error) =>
            logger.error(s"Notification ($push) sent but report could not be stored ($error)")
            Created(toJson(PushResult(push.notification.id).withReportingError(error)))
        }
      case (rejected@_ :: _, reports@_ :: _) =>
        reportPushSent(push.notification, reports) map {
          case Right(_) =>
            logger.warn(s"Notification ($push) was rejected by some providers: ($rejected)")
            Created(toJson(PushResult(push.notification.id).withRejected(rejected)))
          case Left(error) =>
            logger.error(s"Notification ($push) was rejected by some providers and there was error in reporting")
            Created(toJson(PushResult(push.notification.id).withRejected(rejected).withReportingError(error)))
        }
      case (allRejected@_ :: _, Nil) =>
        logger.error(s"Notification ($push) could not be sent: $allRejected")
        Future.successful(InternalServerError)
      case _ =>
        Future.successful(NotFound)
    }
  }

  private def sendPush(push: Push): Future[(List[services.SenderError], List[SenderReport])] = {
    sequence(senders.map(_.sendNotification(push))) map { results =>
      val rejected = results.flatMap(s => s.swap.toOption)
      val reports = results.flatMap(_.toOption)
      (rejected, reports)
    }
  }

  private def prepareReportAndSendPush(push: Push): Future[(List[services.SenderError], List[SenderReport])] = {
      val notificationReport = DynamoNotificationReport(push.notification.id, push.notification.`type`, push.notification, DateTime.now(DateTimeZone.UTC), List(), Some(UUID.randomUUID()), None)
      for {
        initialEmptyNotificationReport <- notificationReportRepository.store(notificationReport)
        sentPush <- initialEmptyNotificationReport match {
          case Left(error) => Future.failed(new Exception(error.message))
          case Right(_) => sendPush(push)
        }
      } yield sentPush

    }

  private def reportPushSent(notification: Notification, reports: List[SenderReport]): Future[RepositoryResult[Unit]] =
    notificationReportRepository.update(DynamoNotificationReport.create(notification, reports, Some(UUID.randomUUID())))
}

