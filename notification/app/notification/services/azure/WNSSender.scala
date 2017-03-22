package notification.services.azure

import azure.NotificationHubClient
import notification.services.Configuration
import tracking.TopicSubscriptionsRepository

import scala.concurrent.ExecutionContext

class WNSSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationsHubSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  override protected val converter = new WNSPushConverter(configuration)
}
