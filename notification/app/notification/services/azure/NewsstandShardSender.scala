package notification.services.azure

import azure.NotificationHubClient
import models.Notification
import models.NotificationType.NewsstandShard
import notification.services.Configuration
import tracking.TopicSubscriptionsRepository

import scala.concurrent.ExecutionContext

class NewsstandShardSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
                          (implicit ec: ExecutionContext) extends NotificationsHubSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  override protected val converter = new APNSPushConverter(configuration)

  override protected def shouldSendToApps(notification: Notification) = notification.`type` == NewsstandShard


}