package notification.services.azure

import azure.NotificationHubClient
import models.{Notification, Platform, iOS}
import models.NotificationType.ElectionsAlert
import notification.services.Configuration
import tracking.TopicSubscriptionsRepository

import scala.concurrent.ExecutionContext

class APNSSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationsHubSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  override protected def platform: Platform = iOS

  override protected val converter = new APNSPushConverter(configuration)

  override protected def shouldSendToApps(notification: Notification) =
    super.shouldSendToApps(notification) && !filterAlert(notification)

  private def filterAlert(notification: Notification) =
    notification.`type` == ElectionsAlert && configuration.disableElectionNotificationsIOS
}