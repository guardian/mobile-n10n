package notifications.providers

import models.{UserId, Platform, Registration}

import scala.concurrent.Future
import scalaz.\/

case class RegistrationResponse(deviceId: String, platform: Platform, userId: UserId)

trait NotificationRegistrar {
  def register(registration: Registration): Future[Error \/ RegistrationResponse]
}
