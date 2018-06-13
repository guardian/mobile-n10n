package notification.services.azure

import java.util.UUID

import azure.{APNSRawPush, Tags}
import azure.apns.{APS, Body, LegacyProperties}
import models.Importance.Major
import models.NotificationType.NewsstandShard
import models.TopicTypes.Newsstand
import models.{Importance, NewsstandShardNotification, Topic, TopicTypes}
import notification.models.{Destination, Push}
import notification.models.ios.{NewsstandNotification, NewsstandNotificationShard}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

class APNSPushConverterSpec extends Specification with Mockito {
  "APNSPushConverter" should {
    "convert Newsstand Shards Notification" in {
      val config = mock[Configuration]
      val aPNSPushConverter = new APNSPushConverter(new notification.services.Configuration(config))
      val newsstandShardNotification = NewsstandShardNotification(
        UUID.randomUUID(),
        3
      )


      val maybeAPNSRawPush = aPNSPushConverter.toRawPush(Push(newsstandShardNotification, Left(newsstandShardNotification.topic)))
      maybeAPNSRawPush must be_==(Some(APNSRawPush(
        body = Body(
          aps = APS(
            alert = None,
            `content-available` = Some(1),
            sound = None
          ),
          customProperties = LegacyProperties(Map.empty)
        ),
        tags = Some(Tags.fromTopics(Set(Topic(TopicTypes.NewsstandShard, "newsstand-shard-3"))))
      )))
    }
  }
}
