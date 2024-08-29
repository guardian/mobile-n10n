package notification.controllers

import models.TopicTypes.Breaking

import java.util.UUID
import models._
import notification.{DateTimeFreezed, NotificationsFixtures}
import notification.services.{NewsstandSender, _}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification
import tracking.InMemoryNotificationReportRepository

import scala.concurrent.Future
import notification.authentication.NotificationAuthAction
import play.api.test.Helpers.stubControllerComponents
import cats.syntax.either._
import metrics.CloudWatchMetrics
import org.joda.time.DateTime

import java.time.Instant


class MainSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Mockito with JsonMatchers with DateTimeFreezed {
  "Sending notification to topics" should {
    "successfully send a notification to multiple topics" in new MainScope {
      val request = requestWithValidTopics
      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      pushSent must beSome.which(_.topic.toSet must beEqualTo(validTopics.toSet))
    }
    "refuse a notification with an invalid key" in new MainScope {
      val request = invalidAuthenticatedRequest.withBody(breakingNewsNotification(validTopics))
      val response = main.pushTopics()(request)

      status(response) must equalTo(UNAUTHORIZED)
    }
    "refuse a notification that is sent twice" in new MainScope {
      val request = requestWithValidTopics
      val firstResponse = main.pushTopics()(request)
      status(firstResponse) must equalTo(CREATED)
      val secondResponse = main.pushTopics()(request)
      status(secondResponse) must equalTo(BAD_REQUEST)
    }
    "refuse a notification without a topic" in new MainScope {
      val request = authenticatedRequest.withBody(breakingNewsNotification(List()))
      status(main.pushTopics()(request)) must equalTo(BAD_REQUEST)
    }
    "refuse a notification with too many topics" in new MainScope {
      val topics = (1 to 21).map(i => Topic(Breaking, s"$i"))
      val request = authenticatedRequest.withBody(breakingNewsNotification(List()))
      status(main.pushTopics()(request)) must equalTo(BAD_REQUEST)
    }
    "fail gracefully for non-fatal errors" in new MainScope {
      val request = requestWithValidTopics
      status(badMain.pushTopics()(request)) must equalTo(INTERNAL_SERVER_ERROR)
    }
  }

  "adding a new notification topic" should {
    "successfully add a new topic and send a notification to it" in new MainScope {
      val newTopic = Topic(Breaking, "new-topic")
      val topicsWithNew = validTopics :+ newTopic
      val request = authenticatedRequest.withBody(breakingNewsNotification(topicsWithNew))
      val response = main.pushTopics()(request)

      status(response) should equalTo(CREATED)
      pushSent must beSome.which(_.topic.toSet must beEqualTo(topicsWithNew.toSet))
    }
  }

  "removing a new notification topic" should {
    "successfully remove a topic and stop sending notifications to it" in new MainScope {
      val remainingTopics = validTopics.tail
      val request = authenticatedRequest.withBody(breakingNewsNotification(remainingTopics))
      val response = main.pushTopics()(request)

      status(response) should equalTo(CREATED)
      pushSent must beSome.which(_.topic.toSet must beEqualTo(remainingTopics.toSet))
    }
  }

  "Sending correct notification" should {
    "notify reporting repository about added notifications" in new MainScope {
      val request = requestWithValidTopics
      val expectedReport = reportWithSenderReports(List(
        senderReport(Senders.AzureNotificationsHub)
      ))
      val response = main.pushTopics()(request)
      status(response) must equalTo(CREATED)
      val dateTime = DateTime.now
      def normalise(notificationReport: NotificationReport) = notificationReport.copy(
        sentTime = dateTime,
        reports = notificationReport.reports.map(_.copy(sentTime = dateTime)),
        ttl = Some(dateTime.plusMonths(3).getMillis / 1000)
      )

      reportRepository.getByUuid(expectedReport.notification.id).map(_.map(normalise)) must equalTo(Right(normalise(expectedReport))).await
    }
  }

  "Sending notification for newsstand" should {
    "successfully send a notification for newsstand" in new MainScope {
      val request = authenticatedRequest
      val id = UUID.randomUUID()
      val response = main.pushNewsstand()(request)

      status(response) must equalTo(CREATED)

      there was one(newsstandNotificationSender).sendNotification(any)
    }

    "refuse a newsstand notification with an invalid key" in new MainScope {
      val request = invalidAuthenticatedRequest
      val response = main.pushNewsstand()(request)

      status(response) must equalTo(UNAUTHORIZED)
    }

    "Send a newstand notification shard" in new MainScope {
      val request = authenticatedRequest.withBody(newsstandShardNotification())
      val response = main.pushTopics(request)
      status(response) must equalTo(CREATED)
      pushSent must beSome.which(_.topic.toSet must beEqualTo(validNewsstandNotificationsTopic.toSet))

    }
  }

  trait NotificationSenderSupportScope extends Scope {
    self: NotificationsFixtures =>
    var pushSent: Option[Notification] = None

    val newsstandNotificationSender = mock[NewsstandSender]
    newsstandNotificationSender.sendNotification(any[UUID]) returns Future.successful(Right(Some("")))
    val mockNotificationSender = {
      new NotificationSender {
        override def sendNotification(notification: Notification, notificationAppSentTime: Instant): Future[SenderResult] = {
          pushSent = Some(notification)
          Future.successful(Right(senderReport(Senders.AzureNotificationsHub)))
        }
      }
    }

    val mockBadNotificationSender = {
      new NotificationSender {
        override def sendNotification(notification: Notification, notificationAppSentTime: Instant): Future[SenderResult] = {
          Future.failed(new Throwable("non fatal error"))
        }
      }
    }
  }

  trait MainScope extends Scope
    with NotificationSenderSupportScope
    with NotificationsFixtures {
    val conf: Configuration = {
      val m = mock[Configuration]
      m.apiKeys returns Set(apiKey)
      m.newsstandRestrictedApiKeys returns Set(apiKey)
      m
    }

    val fastlyPurge = new FastlyPurge {
      override def softPurge(url: String): Future[Boolean] = Future.successful(true)
    }
    val articlePurge = new ArticlePurge(fastlyPurge)

    val controllerComponents = stubControllerComponents()
    val reportRepository = new InMemoryNotificationReportRepository
    val authAction = new NotificationAuthAction(conf, controllerComponents)
    val metrics = mock[CloudWatchMetrics]
    val sloTrackingSender = mock[SloTrackingSender]

    val main = new Main(
      configuration = conf,
      notificationSender = mockNotificationSender,
      newsstandSender = newsstandNotificationSender,
      metrics = metrics,
      notificationReportRepository = reportRepository,
      articlePurge = articlePurge,
      controllerComponents = controllerComponents,
      authAction = authAction,
      sloTrackingSender = sloTrackingSender
    )

    val badMain = new Main(
      configuration = conf,
      notificationSender = mockBadNotificationSender,
      newsstandSender = newsstandNotificationSender,
      metrics = metrics,
      notificationReportRepository = reportRepository,
      articlePurge = articlePurge,
      controllerComponents = controllerComponents,
      authAction = authAction,
      sloTrackingSender = sloTrackingSender
    )
  }

}
