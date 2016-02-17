package notification.controllers

import models.TopicTypes.Breaking
import models._
import notification.NotificationsFixtures
import notification.models.{PushResult, Push}
import notification.services.frontend.{FrontendAlerts, FrontendAlertsSupport}
import notification.services._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.ws.WSClient
import play.api.test.PlaySpecification
import tracking.{RepositoryError, SentNotificationReportRepository}

import scala.concurrent.Future
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
      there was one(repositorySupport.notificationReportRepository).store(notificationReport)
    }

    "notify reporting repository about added notifications and propagate reporting error" in new MainScope {
      val request = requestWithValidTopics
      reportRepository.store(notificationReport) returns Future.successful(RepositoryError("error").left)

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      contentAsJson(response).as[PushResult].reportingError must beSome
    }

    "report frontend alerts rejected notifications" in new MainScope {
      val request = requestWithValidTopics
      alertsSupport.frontendAlerts.sendNotification(any) returns Future.successful(NotificationRejected(Some(providerError)).left)

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      contentAsJson(response).as[PushResult].rejectedNotifications must beSome.which(_.length == 1)
    }

    "send notification to frontend news alerts" in new MainScope {
      val request = requestWithValidTopics

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      there was one(alertsSupport.frontendAlerts).sendNotification(any)
    }

    "report each succesfully sent notification" in new MainScope {
      val request = requestWithValidTopics

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      there was two(reportRepository).store(any[NotificationReport])
    }

    "aggregates errors thrown from reporting" in new MainScope {
      val request = requestWithValidTopics
      val firstError = Future.successful(RepositoryError("first error").left)
      val secondError = Future.successful(RepositoryError("second error").left)

      reportRepository.store(any[NotificationReport]) returns firstError thenReturns secondError

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      contentAsJson(response).as[PushResult].reportingError must beSome(contain("first")) and beSome(contain("second"))
      there was two(reportRepository).store(any[NotificationReport])
    }
  }

  trait NotificationSenderSupportScope extends Scope {
    self: NotificationsFixtures =>
    var pushSent: Option[Push] = None

    val notificationSenderSupport = {
      val m = mock[NotificationSenderSupport]
      m.notificationSender returns new NotificationSender {
        override def sendNotification(push: Push): Future[SenderResult] = {
          pushSent = Some(push)
          Future.successful(notificationReport.right)
        }
      }
    }
  }

  trait NotificationReportRepositorySupportScope extends Scope {
    val reportRepository = mock[SentNotificationReportRepository]
    reportRepository.store(any) returns Future.successful(().right)

    val repositorySupport = new NotificationReportRepositorySupport {
      override val notificationReportRepository = reportRepository
    }
  }

  trait FrontendAlertsSupportScope extends Scope {
    self: NotificationsFixtures =>
    val configuration = mock[Configuration]
    val wsClient = mock[WSClient]
    configuration.frontendNewsAlertApiKey returns "someKey"
    configuration.frontendNewsAlertEndpoint returns "https://internal-frontend.code.dev-guardianapis.com/news-alert/"
    val alertsSupport = new FrontendAlertsSupport(configuration, wsClient) {
      override val frontendAlerts = mock[FrontendAlerts]
    }
    alertsSupport.frontendAlerts.sendNotification(any) returns Future.successful(notificationReport.right)
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
