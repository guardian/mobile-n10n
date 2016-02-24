package notification.controllers

import models.TopicTypes.Breaking
import models._
import notification.NotificationsFixtures
import notification.models.{PushResult, Push}
import notification.services.frontend.{FrontendAlerts, FrontendAlertsSupport}
import notification.services.{NotificationReportRepositorySupport, NotificationSender, NotificationSenderSupport, Configuration}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.ws.WSClient
import play.api.test.PlaySpecification
import providers.ProviderError
import tracking.TrackingObserver

import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

class MainSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Mockito with JsonMatchers {
  "Sending notification to topics" should {
    "successfully send a notification to multiple topics" in new MainScope {
      val request = requestWithValidTopics
      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      pushSent must beSome.which(_.destination must beEqualTo(Left(validTopics)))
    }
    "refuse a notification without a topic" in new MainScope {
      val request = authenticatedRequest.withBody(breakingNewsNotification(Set()))
      status(main.pushTopics()(request)) must equalTo(BAD_REQUEST)
    }
    "refuse a notification with too many topics" in new MainScope {
      val topics = (1 to 21).map(i => Topic(Breaking, s"$i"))
      val request = authenticatedRequest.withBody(breakingNewsNotification(Set()))
      status(main.pushTopics()(request)) must equalTo(BAD_REQUEST)
    }
  }

  "Sending correct notification" should {
    "notify reporting repository about added notifications" in new MainScope {
      val request = requestWithValidTopics

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      there was one(repositorySupport.notificationReportRepository).notificationSent(notificationReport)
    }

    "notify reporting repository about added notifications and propagate tracking error" in new MainScope {
      val request = requestWithValidTopics
      reportRepository.notificationSent(notificationReport) returns Future.successful(trackingError.left)

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      contentAsJson(response).as[PushResult].trackingErrors must beSome
    }

    "notify reporting repository about added notifications and propagate multiple tracking errors" in new MainScope {
      val request = requestWithValidTopics
      reportRepository.notificationSent(notificationReport) returns Future.successful(trackingError.left)
      alertsSupport.frontendAlerts.notificationSent(notificationReport) returns Future.successful(trackingError.left)

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      contentAsJson(response).as[PushResult].trackingErrors must beSome(size[List[String]](2))
    }

    "notify frontend news alerts about notification" in new MainScope {
      val request = requestWithValidTopics

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      there was one(alertsSupport.frontendAlerts).notificationSent(notificationReport)
    }
  }

  trait NotificationSenderSupportScope extends Scope {
    self: NotificationsFixtures =>
    var pushSent: Option[Push] = None

    val notificationSenderSupport = {
      val m = mock[NotificationSenderSupport]
      m.notificationSender returns new NotificationSender {
        override def sendNotification(push: Push): Future[\/[ProviderError, NotificationReport]] = {
          pushSent = Some(push)
          Future.successful(notificationReport.right)
        }
        override def name: String = "test"
      }
    }
  }

  trait NotificationReportRepositorySupportScope extends Scope {
    val reportRepository = mock[TrackingObserver]
    reportRepository.notificationSent(any) returns Future.successful(().right)

    val repositorySupport = new NotificationReportRepositorySupport {
      override val notificationReportRepository = reportRepository
    }
  }

  trait FrontendAlertsSupportScope extends Scope {
    val configuration = mock[Configuration]
    val wsClient = mock[WSClient]
    configuration.frontendNewsAlertApiKey returns "someKey"
    configuration.frontendNewsAlertEndpoint returns "https://internal-frontend.code.dev-guardianapis.com/news-alert/"
    val alertsSupport = new FrontendAlertsSupport(configuration, wsClient) {
      override val frontendAlerts = mock[FrontendAlerts]
    }
    alertsSupport.frontendAlerts.notificationSent(any) returns Future.successful(().right)
  }

  trait MainScope extends Scope
    with NotificationSenderSupportScope
    with NotificationReportRepositorySupportScope
    with FrontendAlertsSupportScope
    with NotificationsFixtures {
    val conf: Configuration = {
      val m = mock[Configuration]
      m.apiKey returns Some(apiKey)
      m
    }

    val main = new Main(
      configuration = conf,
      notificationSenderSupport = notificationSenderSupport,
      notificationReportRepositorySupport = repositorySupport,
      frontendAlertsSupport = alertsSupport
    )
  }
}
