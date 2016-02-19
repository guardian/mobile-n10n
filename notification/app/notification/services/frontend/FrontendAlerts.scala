package notification.services.frontend

import models.{BreakingNewsNotification, NotificationReport}
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.mvc.Http.Status.CREATED
import tracking.{TrackingError, TrackerResult, TrackingObserver}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

class FrontendAlerts(config: FrontendAlertsConfig, wsClient: WSClient) extends TrackingObserver {
  val logger = Logger(classOf[FrontendAlerts])

  def sendBreakingNewsAlert(alert: NewsAlert): Future[String \/ Unit] = wsClient
    .url(config.endpoint + "/alert")
    .withHeaders("Content-Type" -> "application/json", "X-Gu-Api-Key" -> config.apiKey)
    .post(Json.toJson(alert))
    .map { response =>
      if (response.status == CREATED)
        ().right
      else {
        val msg = s"Failed sending breaking news alert, WS returned status code: ${response.status}"
        logger.error(msg)
        msg.left
      }
    }

  override def notificationSent(report: NotificationReport): Future[TrackerResult[Unit]] = report.notification match {
    case bn: BreakingNewsNotification =>
      NewsAlert.fromNotification(bn, report.sentTime) match {
        case Some(alert) =>
          sendBreakingNewsAlert(alert) map { _.leftMap(error => FrontendAlertsTrackingError(error)) }
        case _ =>
          logger.error("Frontend alert not sent. Could not create alert from report ($report).")
          Future.successful(().right)
      }
    case _ =>
      logger.info(s"Frontend alert not sent. Push report ($report) ignored as it does not contain BreakingNews.")
      Future.successful(().right)
  }
}

case class FrontendAlertsTrackingError(description: String) extends TrackingError
