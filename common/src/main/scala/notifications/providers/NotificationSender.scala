package notifications.providers

import models.Push

import scala.concurrent.Future
import scalaz.\/

trait NotificationSender {
  def name: String
  def sendNotification(push: Push): Future[Error \/ Unit]
}