package notification.services

import models.NotificationReport
import notification.models.Push
import providers.Error

import scala.concurrent.Future
import scalaz.\/

trait NotificationSender {
  def name: String
  def sendNotification(push: Push): Future[Error \/ NotificationReport]
}
