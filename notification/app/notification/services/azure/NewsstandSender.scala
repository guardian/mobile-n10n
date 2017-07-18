package notification.services.azure

import java.util.UUID

import azure.{APNSRawPush, NotificationHubClient, Tags}
import models.Topic
import models.TopicTypes.Newsstand
import notification.models.ios.NewsstandNotification

class NewsstandSender(hubClient: NotificationHubClient) {

  def sendNotification(id: UUID) = {
    val push = APNSRawPush(
      body = NewsstandNotification(id).payload,
      tags = Some(Tags.fromTopics(Set(Topic(Newsstand, "newsstand"))))
    )
    hubClient.sendNotification(push)
  }
}
