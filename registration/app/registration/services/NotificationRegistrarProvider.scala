package registration.services

import error.{NotificationsError, RequestError}
import models._
import registration.services.azure.{APNSNotificationRegistrar, GCMNotificationRegistrar, NewsstandNotificationRegistrar, WindowsNotificationRegistrar}

import scala.concurrent.ExecutionContext
import cats.data.Xor
import cats.implicits._

trait RegistrarProvider {
  def registrarFor(registration: Registration): Xor[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform)

  def registrarFor(platform: Platform): Xor[NotificationsError, NotificationRegistrar]
}

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

final class NotificationRegistrarProvider(
  windowsRegistrar: WindowsNotificationRegistrar,
  gcmRegistrar: GCMNotificationRegistrar,
  apnsRegistrar: APNSNotificationRegistrar,
  newsstandRegistrar: NewsstandNotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  override def registrarFor(platform: Platform): NotificationsError Xor NotificationRegistrar = platform match {
    case WindowsMobile => windowsRegistrar.right
    case Android => gcmRegistrar.right
    case `iOS` => apnsRegistrar.right
    case Newsstand => newsstandRegistrar.right
    case _ => UnsupportedPlatform(platform.toString).left
  }
}
