package notification.services.azure

import azure.NotificationHubClient
import azure.NotificationHubClient._
import notification.models.Push
import notification.services.Configuration
import tracking.TopicSubscriptionsRepository
import scala.concurrent.{ExecutionContext, Future}

class GCMSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationsHubSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  protected val azureRawPushConverter = new GCMPushConverter(configuration)

  override protected def send(push: Push): Future[HubResult[Unit]] =
    hubClient.sendGCMNotification(azureRawPushConverter.toRawPush(push))
}