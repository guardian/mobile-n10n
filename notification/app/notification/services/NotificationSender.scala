package notification.services

import notification.models.Push

import scala.concurrent.Future

trait NotificationSender {
  def sendNotification(push: Push): Future[SenderResult]
}
