package notification.services.azure

import java.util.UUID

import azure.apns.{APS, Body, LegacyProperties}
import azure.{APNSRawPush, NotificationHubClient, Tags}
import models.Topic
import models.TopicTypes.Newsstand
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class NewsstandSenderTest extends Specification with Mockito {
  "Newsstand sender" should {
    "Send a simple newsstand notification" in {
      val hubClient = mock[NotificationHubClient]
      val sender = new NewsstandSender(hubClient)
      sender.sendNotification(UUID.randomUUID())
      val rawPush = APNSRawPush(
        body = Body(
          aps = APS(
            alert = None,
            `content-available` = Some(1),
            sound = None
          ),
          customProperties = LegacyProperties(Map.empty)
        ),
        tags = Some(Tags.fromTopics(Set(Topic(Newsstand, "newsstand"))))
      )
      there was one(hubClient).sendNotification(rawPush)
    }
  }
}
