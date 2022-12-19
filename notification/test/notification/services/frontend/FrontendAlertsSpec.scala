package notification.services.frontend

import java.net.URI
import models.BreakingNewsNotification
import models.Link.External
import notification.NotificationsFixtures
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Scope
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.ws.{WSClient, WSRequest}
import play.core.server.Server
import play.api.routing.sird._
import play.api.mvc._
import play.api.test._

import java.time.Instant

class FrontendAlertsSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "Frontend alerts notified about notification" should {
    "skip breaking news notification without capi id (i.e. with non-internal Link)" in new FrontendAlertsScope {

      val push = contentTargetedBreakingNewsPush().asInstanceOf[BreakingNewsNotification].copy(link = External("url"))

      alerts.sendNotification(push, Instant.now()) must beEqualTo(Left(FrontendAlertsProviderError("Alert could not be created"))).await

      there was no(wsClient).url(any)
    }

    "successfully send notification" in new FrontendAlertsScope  {
      Server.withRouterFromComponents() { cs => {
        case POST(p"/alert") => cs.defaultActionBuilder {
          Results.Created
        }
      }
      } { implicit port =>
        WsTestClient.withClient { client =>
          val alerts = new FrontendAlerts(config, client)
          alerts.sendNotification(contentTargetedBreakingNewsPush(), Instant.now()) must beRight.await
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
