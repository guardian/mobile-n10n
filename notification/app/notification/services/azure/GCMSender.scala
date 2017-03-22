package notification.services.azure

import azure.NotificationHubClient
import models.NotificationType.ElectionsAlert
import models.Notification
import notification.services.Configuration
import tracking.TopicSubscriptionsRepository

import scala.concurrent.ExecutionContext

class GCMSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationsHubSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  override protected val converter = new GCMPushConverter(configuration)

  override protected def shouldSendToApps(notification: Notification) =
    super.shouldSendToApps(notification) && !filterAlert(notification)

  private def filterAlert(notification: Notification) =
    notification.`type` == ElectionsAlert && configuration.disableElectionNotificationsAndroid

}