package notification.services.frontend

import java.net.URI

import models.{BreakingNewsNotification, GoalAlertNotification}
import models.Link.External
import models.NotificationType.GoalAlert
import notification.NotificationsFixtures
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Scope
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.ws.{WSRequest, WSClient}
import play.core.server.Server
import tracking.TrackingError

import play.api.routing.sird._
import play.api.mvc._
import play.api.test._

import scalaz.syntax.either._

class FrontendAlertsSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "Frontend alerts notified about notification" should {
    "skip non breaking news notifications" in new FrontendAlertsScope {
      val report = notificationReport.copy(`type` = GoalAlert, notification = mock[GoalAlertNotification])

      alerts.notificationSent(report) must beEqualTo(().right[TrackingError]).await

      there was no(wsClient).url(any)
    }

    "skip breaking news notification without capi id (i.e. with non-internal Link)" in new FrontendAlertsScope {
      val withExternalLink = breakingNewsNotification(validTopics).asInstanceOf[BreakingNewsNotification].copy(link = External("url"))
      val report = notificationReport.copy(notification = withExternalLink)

      alerts.notificationSent(report) must beEqualTo(().right[TrackingError]).await

      there was no(wsClient).url(any)
    }

    "successfully send notification" in new FrontendAlertsScope  {
      Server.withRouter() {
        case POST(p"/alert") => Action {
          Results.Created
        }
      } { implicit port =>
        WsTestClient.withClient { client =>
          val alerts = new FrontendAlerts(config, client)
          alerts.notificationSent(notificationReport) must beEqualTo(().right[TrackingError]).await
        }
      }
    }
  }

  trait FrontendAlertsScope extends Scope with NotificationsFixtures {
    val config = FrontendAlertsConfig(new URI(""), "dev-key")
    val wsClient = mock[WSClient]
    val wsRequest = mock[WSRequest]
    wsClient.url(any) returns wsRequest
    wsRequest.defaultReturn(wsRequest)
    val alerts = new FrontendAlerts(config, wsClient)
  }

}
