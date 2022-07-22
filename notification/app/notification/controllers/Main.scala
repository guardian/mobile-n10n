package notification.controllers

import java.util.UUID
import java.time.{Duration, Instant}
import authentication.AuthAction
import com.amazonaws.services.cloudwatch.model.StandardUnit
import metrics.{CloudWatchMetrics, MetricDataPoint}
import models.{TopicTypes, _}
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import notification.models.PushResult
import notification.services
import notification.services.{ArticlePurge, Configuration, NewsstandSender, NotificationSender}
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json.toJson
import play.api.mvc._
import tracking.Repository.RepositoryResult
import tracking.SentNotificationReportRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.MapHasAsJava

final class Main(
  configuration: Configuration,
  notificationSender: NotificationSender,
  newsstandSender: NewsstandSender,
  notificationReportRepository: SentNotificationReportRepository,
  articlePurge: ArticlePurge,
  metrics: CloudWatchMetrics,
  controllerComponents: ControllerComponents,
  authAction: AuthAction
)(implicit executionContext: ExecutionContext)
  extends AbstractController(controllerComponents) {

  implicit private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  implicit def mapToContext(c: Map[String, _]): LogstashMarker = appendEntries(c.asJava)

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
    val notificationReceivedTime = Instant.now
    val notification = request.body
    val topics = notification.topic
    val MaxTopics = 20
    (topics.size match {
      case 0 => Future.successful(BadRequest("Empty topic list"))
      case a: Int if a > MaxTopics => Future.successful(BadRequest(s"Too many topics, maximum: $MaxTopics"))
      case _ if !topics.forall{topic => request.isPermittedTopicType(topic.`type`)} =>
        Future.successful(Unauthorized(s"This API key is not valid for ${topics.filterNot(topic => request.isPermittedTopicType(topic.`type`))}."))
      case _ =>
        val result = pushWithDuplicateProtection(notification)
        val durationMillis = Duration.between(notificationReceivedTime, Instant.now).toMillis
        result.foreach(_ => logger.info(
          Map(
            "notificationId" -> notification.id,
            "notificationType" -> notification.`type`.toString,
            "notificationTitle" -> notification.title.getOrElse("Unknown"),
            "notificationApp.notificationProcessingTime" -> durationMillis,
            "notificationApp.notificationReceivedTime.millis" -> notificationReceivedTime.toEpochMilli,
            "notificationApp.notificationReceivedTime.string" -> notificationReceivedTime.toString,
          ),
        s"Spent $durationMillis milliseconds processing notification ${notification.id}"))
        result
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

  private def decacheArticle(notification: Notification): Future[Unit] = {
    articlePurge.purgeFromNotification(notification)
      .map(_ => ())
      .recover {
      case NonFatal(e) =>
        logger.warn(s"Unable to decache article for notification ${notification.id}", e)
        ()
    }
  }

  private def prepareReportAndSendPush(notification: Notification): Future[Either[services.SenderError, SenderReport]] = {
    val notificationReport = NotificationReport.create(notification.id, notification.`type`, notification, DateTime.now(DateTimeZone.UTC), List(), None)
    for {
      initialEmptyNotificationReport <- notificationReportRepository.store(notificationReport)
      _ <- decacheArticle(notification)
      sentPush <- initialEmptyNotificationReport match {
        case Left(error) => Future.failed(new Exception(error.message))
        case Right(_) => notificationSender.sendNotification(notification)
      }
    } yield sentPush
  }

  private def reportPushSent(notification: Notification, reports: List[SenderReport]): Future[RepositoryResult[Unit]] =
    notificationReportRepository.update(NotificationReport.create(notification, reports))
}

