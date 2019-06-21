package notification.controllers

import java.util.UUID

import authentication.AuthAction
import com.amazonaws.services.cloudwatch.model.StandardUnit
import metrics.{CloudWatchMetrics, MetricDataPoint}
import models.{TopicTypes, _}
import notification.models.PushResult
import notification.services
import notification.services.{Configuration, NewsstandSender, NotificationSender}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc._
import tracking.Repository.RepositoryResult
import tracking.SentNotificationReportRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

final class Main(
  configuration: Configuration,
  notificationSender: NotificationSender,
  newsstandSender: NewsstandSender,
  notificationReportRepository: SentNotificationReportRepository,
  metrics: CloudWatchMetrics,
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

  def pushNewsstand: Action[AnyContent] = authAction.async { request =>
    if(request.isPermittedTopicType(TopicTypes.Newsstand)){
      val id = UUID.randomUUID()
      newsstandSender.sendNotification(id) map { _ =>
        logger.info("Newsstand notification sent")
        metrics.send(MetricDataPoint(name = "SuccessfulNewstandSend", value = 1, unit = StandardUnit.Count))
        Created(toJson(PushResult(id)))
      } recover {
        case NonFatal(error) =>
          logger.error(s"Newsstand notification failed: $error")
          metrics.send(MetricDataPoint(name = "SuccessfulNewstandSend", value = 0, unit = StandardUnit.Count))
          InternalServerError(s"Newsstand notification failed: $error")
      }
    }
    else {
      Future.successful(Unauthorized(s"This API key is not valid for ${TopicTypes.Newsstand}."))
    }
  }

  def pushTopics: Action[Notification] = authAction.async(parse.json[Notification]) { request =>
    val notification = request.body
    val topics = notification.topic
    val MaxTopics = 3
    (topics.size match {
      case 0 => Future.successful(BadRequest("Empty topic list"))
      case a: Int if a > MaxTopics => Future.successful(BadRequest(s"Too many topics, maximum: $MaxTopics"))
      case _ if !topics.forall{topic => request.isPermittedTopicType(topic.`type`)} =>
        Future.successful(Unauthorized(s"This API key is not valid for ${topics.filterNot(topic => request.isPermittedTopicType(topic.`type`))}."))
      case _ => pushWithDuplicateProtection(notification)
    }) recoverWith {
      case NonFatal(exception) => {
        logger.warn(s"Pushing notification failed: $notification", exception)
        Future.successful(InternalServerError)
      }
    }
  }

  private def pushWithDuplicateProtection(notification: Notification): Future[Result] = {
    val isDuplicate = notificationReportRepository.getByUuid(notification.id).map(_.isRight)

    isDuplicate.flatMap {
      case true => Future.successful(BadRequest(s"${notification.id} has been sent before - refusing to resend"))
      case false => pushGeneric(notification)
    }
  }

  private def pushGeneric(notification: Notification) = {
    prepareReportAndSendPush(notification) flatMap {
      case Right(report) =>
        reportPushSent(notification, List(report)) map {
          case Right(_) =>
            logger.info(s"Notification was sent: $notification")
            Created(toJson(PushResult(notification.id)))
          case Left(error) =>
            logger.error(s"Notification ($notification) sent but report could not be stored ($error)")
            Created(toJson(PushResult(notification.id).withReportingError(error)))
        }
      case Left(error) =>
        logger.error(s"Notification ($notification) could not be sent: $error")
        Future.successful(InternalServerError)
    }
  }

  private def prepareReportAndSendPush(notification: Notification): Future[Either[services.SenderError, SenderReport]] = {
    val notificationReport = NotificationReport.create(notification.id, notification.`type`, notification, DateTime.now(DateTimeZone.UTC), List(), None)
    for {
      initialEmptyNotificationReport <- notificationReportRepository.store(notificationReport)
      sentPush <- initialEmptyNotificationReport match {
        case Left(error) => Future.failed(new Exception(error.message))
        case Right(_) => notificationSender.sendNotification(notification)
      }
    } yield sentPush
  }

  private def reportPushSent(notification: Notification, reports: List[SenderReport]): Future[RepositoryResult[Unit]] =
    notificationReportRepository.update(NotificationReport.create(notification, reports))
}

