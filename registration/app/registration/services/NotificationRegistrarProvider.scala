package registration.services

import error.{NotificationsError, RequestError}
import models.{Android, iOS, Platform, Registration, WindowsMobile}
import registration.services.azure.{APNSNotificationRegistrar, GCMNotificationRegistrar, WindowsNotificationRegistrar}

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import cats.data.Xor
import cats.implicits._

trait RegistrarProvider {
  def registrarFor(registration: Registration): Xor[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform)

  def registrarFor(platform: Platform): Xor[NotificationsError, NotificationRegistrar]

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T]
}

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

final class NotificationRegistrarProvider(
  windowsNotificationRegistrar: WindowsNotificationRegistrar,
  gcmNotificationRegistrar: GCMNotificationRegistrar,
  apnsNotificationRegistrar: APNSNotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  private val registrars = List(windowsNotificationRegistrar, gcmNotificationRegistrar, apnsNotificationRegistrar)
  private val uniqueProviders: List[NotificationRegistrar] =
    registrars
      .groupBy(_.providerIdentifier)
      .values
      .flatMap(_.headOption)(breakOut)

  override def registrarFor(platform: Platform): NotificationsError Xor NotificationRegistrar = platform match {
    case WindowsMobile => windowsNotificationRegistrar.right
    case Android => gcmNotificationRegistrar.right
    case `iOS` => apnsNotificationRegistrar.right
    case _ => UnsupportedPlatform(platform.toString).left
  }

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T] =
    uniqueProviders.map(fn)
}
