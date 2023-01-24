package notification.services

import models.Notification

import java.time.Instant
import scala.concurrent.Future

trait NotificationSender {
  def sendNotification(notification: Notification, notificationReceivedTime: Instant): Future[SenderResult]
}
