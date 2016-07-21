package registration.services

import error.{NotificationsError, RequestError}
import models.{Android, Platform, Registration, WindowsMobile}
import registration.services.azure.{GCMNotificationRegistrar, WindowsNotificationRegistrar}

import scala.concurrent.ExecutionContext
import scalaz.\/
import scalaz.syntax.either._

trait RegistrarProvider {
  def registrarFor(registration: Registration): \/[NotificationsError, NotificationRegistrar]
}

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

final class NotificationRegistrarProvider(windowsNotificationRegistrar: WindowsNotificationRegistrar, gcmNotificationsRegistrar: GCMNotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  override def registrarFor(registration: Registration): NotificationsError \/ NotificationRegistrar = registration match {
    case Registration(_, WindowsMobile, _, _) => windowsNotificationRegistrar.right
    case Registration(_, Android, _, _) => gcmNotificationsRegistrar.right
    case _ => UnsupportedPlatform(registration.platform.toString).left
  }
}
