package notification.services.azure

import _root_.azure.{GCMRawPush, NotificationHubClient}
import models.Importance.{Major, Minor}
import models._
import notification.services.{Configuration, Senders}
import notification.{DateTimeFreezed, NotificationsFixtures}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import tracking.TopicSubscriptionsRepository

import scala.concurrent.Future
import cats.data.Xor
import cats.implicits._

class GCMSenderSpec(implicit ev: ExecutionEnv) extends Specification
  with Mockito with DateTimeFreezed {

  "the notification sender" should {
    "filter out Minor notifications" in new GCMScope {
      override val importance = Minor
      val expectedReport = senderReport(Senders.AzureNotificationsHub).right
      val result = androidNotificationSender.sendNotification(userPush)

      result should beEqualTo(expectedReport).await
      there was no(hubClient).sendNotification(any[GCMRawPush])
    }

    "process a Minor election notification" in new GCMScope {
      val result = androidNotificationSender.sendNotification(electionPush(Minor))

      result should beEqualTo(senderReport(Senders.AzureNotificationsHub, platformStats = PlatformStatistics(WindowsMobile, 1).some, sendersId = "fake-id".some).right).await
      got {
        one(hubClient).sendNotification(pushConverter.toRawPush(electionPush(Minor)).get)
      }
    }

    "ignore a Minor election notification if election notifications are disabled" in new GCMScope {
      val expectedReport = senderReport(Senders.AzureNotificationsHub).right
      configuration.disableElectionNotificationsAndroid returns true

      val result = androidNotificationSender.sendNotification(electionPush(Minor))
      
      result should beEqualTo(expectedReport).await
      there was no(hubClient).sendNotification(any[GCMRawPush])
    }

    "process a Major notification" in {
      "send two separate with notifications with differently encoded topics when addressed to topic" in new GCMScope {
        val result = androidNotificationSender.sendNotification(topicPush)

        result should beEqualTo(senderReport(Senders.AzureNotificationsHub, platformStats = PlatformStatistics(WindowsMobile, 2).some, sendersId = "fake-id".some).right).await
        got {
          one(hubClient).sendNotification(pushConverter.toRawPush(topicPush).get)
        }
      }

      "send only one notification when destination is user so that user do not receive the same message twice" in new GCMScope {
        val result = androidNotificationSender.sendNotification(userPush)

        result should beEqualTo(senderReport(Senders.AzureNotificationsHub, platformStats = PlatformStatistics(WindowsMobile, 1).some, sendersId = "fake-id".some).right).await
        got {
          one(hubClient).sendNotification(pushConverter.toRawPush(userPush).get)
        }
      }
    }
  }

  trait GCMScope extends Scope with NotificationsFixtures {
    def importance: Importance = Major
    val userPush = userTargetedBreakingNewsPush(importance)
    val topicPush = topicTargetedBreakingNewsPush(
      breakingNewsNotification(Set(
        Topic(TopicTypes.Breaking, "world/religion"),
        Topic(TopicTypes.Breaking, "world/isis")
      ))
    )
    def electionPush(importance: Importance) = topicTargetedBreakingNewsPush(
      electionNotification(importance)
    )

    val configuration = mock[Configuration]
    configuration.debug returns true
    val hubClient = {
      val client = mock[NotificationHubClient]
      client.sendNotification(any[GCMRawPush]) returns Future.successful(Some("fake-id").right)
      client
    }

    val pushConverter = new GCMPushConverter(configuration)

    val topicSubscriptionsRepository = {
      val m = mock[TopicSubscriptionsRepository]
      m.count(any[Topic]) returns Future.successful(1.right)
      m
    }

    val androidNotificationSender = new GCMSender(hubClient, configuration, topicSubscriptionsRepository)
  }
}
