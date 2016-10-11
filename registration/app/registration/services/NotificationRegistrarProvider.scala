package registration.services

import error.{NotificationsError, RequestError}
import models._
import registration.services.azure._

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import cats.data.Xor
import cats.implicits._

trait RegistrarProvider {
  def registrarFor(registration: Registration): Xor[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform, registration.buildTier)

  def registrarFor(platform: Platform, buildTier: Option[String]): Xor[NotificationsError, NotificationRegistrar]

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T]
}

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

final class NotificationRegistrarProvider(
  windowsRegistrar: WindowsNotificationRegistrar,
  gcmRegistrar: GCMNotificationRegistrar,
  apnsRegistrar: APNSNotificationRegistrar,
  apnsEnterpriseRegistrar: APNSEnterpriseNotifcationRegistrar,
  newsstandRegistrar: NewsstandNotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  private val registrars = List(windowsRegistrar, gcmRegistrar, apnsRegistrar, newsstandRegistrar)
  private val uniqueProviders: List[NotificationRegistrar] =
    registrars
      .groupBy(_.hubClient.notificationHubConnection)
      .values
      .flatMap(_.headOption)(breakOut)

  override def registrarFor(platform: Platform, buildTier: Option[String]): NotificationsError Xor NotificationRegistrar = platform match {
    case WindowsMobile => windowsRegistrar.right
    case Android => gcmRegistrar.right
    case `iOS` if buildTier.contains("enterprise-internal") => apnsEnterpriseRegistrar.right
    case `iOS` => apnsRegistrar.right
    case Newsstand => newsstandRegistrar.right
    case _ => UnsupportedPlatform(platform.toString).left
  }

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T] =
    uniqueProviders.map(fn)
}
