package notifications.providers

import models.{UserId, Platform, Registration}

import scala.concurrent.Future
import scalaz.\/

case class RegistrationResponse(deviceId: String, platform: Platform, userId: UserId)

object RegistrationResponse {
  def fromHubResponse(registrationResponse: gu.msnotifications.RegistrationResponse): RegistrationResponse = ???
}

trait NotificationRegistrar {
  def register(registration: Registration): Future[Error \/ RegistrationResponse]
}
