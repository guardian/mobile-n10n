package registration.services

import error.{NotificationsError, RequestError}
import models._

trait RegistrarProvider {
  def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar]

  def registrarFor(
    platform: Platform,
    deviceToken: DeviceToken,
    currentProvider: Option[RegistrationProvider]
  ): Either[NotificationsError, NotificationRegistrar]

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T]
}

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

case class MalformattedRegistration(description: String) extends RequestError {
  override def reason: String = s"Malformatred request: $reason"
}