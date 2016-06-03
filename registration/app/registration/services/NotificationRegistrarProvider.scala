package registration.services

import models.{Registration, WindowsMobile}

import scala.concurrent.ExecutionContext
import scalaz.\/
import scalaz.syntax.either._

trait RegistrarProvider {
  def registrarFor(registration: Registration): \/[String, NotificationRegistrar]
}

final class NotificationRegistrarProvider(windowsNotificationRegistrar: NotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  override def registrarFor(registration: Registration): String \/ NotificationRegistrar = registration match {
    case Registration(_, WindowsMobile, _, _) => windowsNotificationRegistrar.right
    case _ => "Unsupported platform".left
  }
}
