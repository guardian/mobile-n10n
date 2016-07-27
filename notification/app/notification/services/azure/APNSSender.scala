package notification.services.azure

import azure.NotificationHubClient
import azure.NotificationHubClient._
import notification.models.Push
import notification.services.Configuration
import tracking.TopicSubscriptionsRepository
import scala.concurrent.{ExecutionContext, Future}

class APNSSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationsHubSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  protected val azureRawPushConverter = new APNSPushConverter(configuration)

  override protected def send(push: Push): Future[HubResult[Unit]] =
    hubClient.sendAPNSNotification(azureRawPushConverter.toRawPush(push))
}