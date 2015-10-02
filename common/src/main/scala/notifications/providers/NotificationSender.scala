package notifications.providers

import models.{NotificationReport, Push}

import scala.concurrent.Future
import scalaz.\/

trait NotificationSender {
  def name: String
  def sendNotification(push: Push): Future[Error \/ NotificationReport]
}