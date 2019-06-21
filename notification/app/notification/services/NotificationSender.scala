package notification.services

import models.Notification

import scala.concurrent.Future

trait NotificationSender {
  def sendNotification(notification: Notification): Future[SenderResult]
}
