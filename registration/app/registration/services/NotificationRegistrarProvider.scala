package registration.services

import models.{Android, Registration, WindowsMobile}
import registration.services.azure.{GCMNotificationRegistrar, WindowsNotificationRegistrar}

import scala.concurrent.ExecutionContext
import scalaz.\/
import scalaz.syntax.either._

trait RegistrarProvider {
  def registrarFor(registration: Registration): \/[String, NotificationRegistrar]
}

final class NotificationRegistrarProvider(windowsNotificationRegistrar: WindowsNotificationRegistrar, gcmNotificationsRegistrar: GCMNotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  override def registrarFor(registration: Registration): String \/ NotificationRegistrar = registration match {
    case Registration(_, WindowsMobile, _, _) => windowsNotificationRegistrar.right
    case Registration(_, Android, _, _) => gcmNotificationsRegistrar.right
    case _ => "Unsupported platform".left
  }
}
