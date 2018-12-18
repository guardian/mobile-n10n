package notification.services.frontend

import java.net.URI

import models.{BreakingNewsNotification, SenderReport}
import notification.models.Push
import notification.services.{NotificationSender, SenderError, SenderResult, Senders}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.mvc.Http.Status.CREATED

import scala.concurrent.{ExecutionContext, Future}

case class FrontendAlertsConfig(endpoint: URI, apiKey: String)

class FrontendAlerts(config: FrontendAlertsConfig, wsClient: WSClient)(implicit val ec: ExecutionContext) extends NotificationSender {
  val logger = Logger(classOf[FrontendAlerts])

  def sendBreakingNewsAlert(alert: NewsAlert): Future[Either[String, Unit]] =
    wsClient.url(s"${ config.endpoint }/alert")
    .addHttpHeaders("Content-Type" -> "application/json", "X-Gu-Api-Key" -> config.apiKey)
    .post(Json.toJson(alert))
    .map { response =>
      if (response.status == CREATED)
        Right(())
      else {
        val msg = s"Failed sending breaking news alert, WS returned status code: ${ response.status }"
        logger.error(msg)
        Left(msg)
      }
    }

  override def sendNotification(push: Push): Future[SenderResult] = push.notification match {
    case bn: BreakingNewsNotification =>
      sendAsBreakingNewsAlert(push, bn)
    case _ =>
      logger.info(s"Frontend alert not sent. Push report ($push) ignored as notification is not BreakingNews.")
      Future.successful {
        Left(FrontendAlertsProviderError("Only Breaking News notification currently supported"))
      }
  }

  private def sendAsBreakingNewsAlert(push: Push, bn: BreakingNewsNotification) = {
    NewsAlert.fromNotification(bn, DateTime.now) match {
      case Some(alert) =>
        sendBreakingNewsAlert(alert) map {
          case Right(()) => Right(SenderReport(Senders.FrontendAlerts, alert.publicationDate))
          case Left(e) => Left(FrontendAlertsProviderError(s"Could not send breaking news alert ($e)"))
        }
      case _ =>
        logger.error(s"Frontend alert not sent. Could not create alert from notification ${ push.notification }")
        Future.successful {
          Left(FrontendAlertsProviderError("Alert could not be created"))
        }
    }
  }
}

case class FrontendAlertsProviderError(reason: String) extends SenderError {
  override def senderName: String = Senders.FrontendAlerts
}
