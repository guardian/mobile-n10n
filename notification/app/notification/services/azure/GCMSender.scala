package notification.services.azure

import azure.NotificationHubClient
import azure.NotificationHubClient._
import models.NotificationType.ElectionsAlert
import models.{Notification, NotificationType}
import notification.models.Push
import notification.services.Configuration
import tracking.TopicSubscriptionsRepository

import scala.concurrent.{ExecutionContext, Future}

class GCMSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationsHubSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  protected val azureRawPushConverter = new GCMPushConverter(configuration)

  override protected def send(push: Push): Future[HubResult[Option[String]]] =
    hubClient.sendNotification(azureRawPushConverter.toRawPush(push))

  override protected def shouldSendToApps(notification: Notification) =
    super.shouldSendToApps(notification) && !filterAlert(notification)

  private def filterAlert(notification: Notification) =
    notification.`type` == ElectionsAlert && configuration.disableElectionNotificationsAndroid

}