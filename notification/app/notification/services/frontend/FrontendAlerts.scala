package notification.services.frontend

import error.NotificationsError
import models.{SenderReport, BreakingNewsNotification}
import notification.models.Push
import notification.services.{Senders, SenderError, NotificationRejected, SenderResult, NotificationSender}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.mvc.Http.Status.CREATED
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scalaz.{-\/, \/-, \/}
import scalaz.syntax.either._
import scalaz.syntax.std.option._

class FrontendAlerts(config: FrontendAlertsConfig, wsClient: WSClient) extends NotificationSender {
  val logger = Logger(classOf[FrontendAlerts])

  def sendBreakingNewsAlert(alert: NewsAlert): Future[String \/ Unit] =
    wsClient.url(s"${ config.endpoint }/alert")
    .withHeaders("Content-Type" -> "application/json", "X-Gu-Api-Key" -> config.apiKey)
    .post(Json.toJson(alert))
    .map { response =>
      if (response.status == CREATED)
        ().right
      else {
        val msg = s"Failed sending breaking news alert, WS returned status code: ${ response.status }"
        logger.error(msg)
        msg.left
      }
    }

  override def sendNotification(push: Push): Future[SenderResult] = push.notification match {
    case bn: BreakingNewsNotification =>
      sendAsBreakingNewsAlert(push, bn)
    case _ =>
      logger.info(s"Frontend alert not sent. Push report ($push) ignored as notification is not BreakingNews.")
      Future.successful {
        NotificationRejected(FrontendAlertsProviderError("Only Breaking News notification currently supported").some).left
      }
  }

  private def sendAsBreakingNewsAlert(push: Push, bn: BreakingNewsNotification) = {
    NewsAlert.fromNotification(bn, DateTime.now) match {
      case Some(alert) =>
        sendBreakingNewsAlert(alert) map {
          case \/-(()) => SenderReport(Senders.FrontendAlerts, alert.publicationDate).right
          case -\/(e) => NotificationRejected(FrontendAlertsProviderError(s"Could not send breaking news alert ($e)").some).left
        }
      case _ =>
        logger.error(s"Frontend alert not sent. Could not create alert from notification ${ push.notification }")
        Future.successful {
          NotificationRejected(FrontendAlertsProviderError("Alert could not be created").some).left
        }
    }
  }
}

case class FrontendAlertsProviderError(reason: String) extends SenderError {
  override def senderName: String = Senders.FrontendAlerts

  override def underlying: Option[NotificationsError] = None
}
