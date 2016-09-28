package notification.controllers

import models.TopicTypes.Breaking
import models._
import notification.{DateTimeFreezed, NotificationsFixtures}
import notification.models.{Push, PushResult}
import notification.services.frontend.FrontendAlerts
import notification.services._
import org.mockito.ArgumentCaptor
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification
import tracking.{InMemoryNotificationReportRepository, RepositoryError, SentNotificationReportRepository}

import scala.concurrent.Future
import cats.data.Xor
import cats.implicits._

class MainSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Mockito with JsonMatchers with DateTimeFreezed {
  "Sending notification to topics" should {
    "successfully send a notification to multiple topics" in new MainScope {
      val request = requestWithValidTopics
      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      pushSent must beSome.which(_.destination must beEqualTo(Left(validTopics)))
    }
    "refuse a notification that is sent twice" in new MainScope {
      val request = requestWithValidTopics
      val firstResponse = main.pushTopics()(request)
      status(firstResponse) must equalTo(CREATED)
      val secondResponse = main.pushTopics()(request)
      status(secondResponse) must equalTo(BAD_REQUEST)
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
      val expectedReport = reportWithSenderReports(List(
        senderReport(Senders.AzureNotificationsHub), senderReport(Senders.FrontendAlerts)
      ))

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      reportRepository.getByUuid(expectedReport.notification.id) must equalTo(expectedReport.right).await
    }

    "report frontend alerts rejected notifications" in new MainScope {
      val request = requestWithValidTopics
      frontendAlerts.sendNotification(any) returns Future.successful(NotificationRejected(Some(providerError)).left)

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      contentAsJson(response).as[PushResult].rejectedNotifications must beSome.which(_.length == 1)
    }

    "send notification to frontend news alerts" in new MainScope {
      val request = requestWithValidTopics

      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      there was one(frontendAlerts).sendNotification(any)
    }


    "notification report has sent time of last sender report" in new MainScope {
      val request = requestWithValidTopics
      val frontendAlertsReport = senderReport(Senders.FrontendAlerts, sentTimeOffsetSeconds = 1)
      frontendAlerts.sendNotification(any) returns Future.successful(frontendAlertsReport.right)

      val response = main.pushTopics()(request)


      status(response) must equalTo(CREATED)

      val sentTime = reportRepository.getByUuid(breakingNewsNotification(Set.empty).id).map(_.map(_.sentTime))

      sentTime must beEqualTo(frontendAlertsReport.sentTime.right).await
    }
  }

  trait NotificationSenderSupportScope extends Scope {
    self: NotificationsFixtures =>
    var pushSent: Option[Push] = None

    val windowsNotificationSender = {
      new NotificationSender {
        override def sendNotification(push: Push): Future[SenderResult] = {
          pushSent = Some(push)
          Future.successful(senderReport(Senders.AzureNotificationsHub).right)
        }
      }
    }
  }

  trait FrontendAlertsScope extends Scope {
    self: NotificationsFixtures =>
    val frontendAlerts = mock[FrontendAlerts]
    frontendAlerts.sendNotification(any) returns Future.successful(senderReport(Senders.FrontendAlerts, None).right)
  }

  trait MainScope extends Scope
    with NotificationSenderSupportScope
    with FrontendAlertsScope
    with NotificationsFixtures {
    val conf: Configuration = {
      val m = mock[Configuration]
      m.apiKeys returns List(apiKey)
      m
    }

    val reportRepository = new InMemoryNotificationReportRepository

    val main = new Main(
      configuration = conf,
      senders = List(windowsNotificationSender, frontendAlerts),
      notificationReportRepository = reportRepository
    )
  }

}
