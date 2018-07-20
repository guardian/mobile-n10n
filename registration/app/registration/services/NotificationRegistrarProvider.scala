package registration.services

import error.{NotificationsError, RequestError}
import models._
import registration.services.azure._

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import cats.implicits._

trait RegistrarProvider {
  def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform)

  def registrarFor(platform: Platform): Either[NotificationsError, NotificationRegistrar]

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T]
}

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

final class NotificationRegistrarProvider(
  gcmRegistrar: GCMNotificationRegistrar,
  apnsRegistrar: APNSNotificationRegistrar,
  newsstandRegistrar: NewsstandNotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  private val registrars = List(gcmRegistrar, apnsRegistrar, newsstandRegistrar)
  private val uniqueProviders: List[NotificationRegistrar] =
    registrars
      .groupBy(_.hubClient.notificationHubConnection)
      .values
      .flatMap(_.headOption)(breakOut)

  override def registrarFor(platform: Platform): Either[NotificationsError, NotificationRegistrar] = platform match {
    case Android => Right(gcmRegistrar)
    case `iOS` => Right(apnsRegistrar)
    case Newsstand => Right(newsstandRegistrar)
    case _ => Left(UnsupportedPlatform(platform.toString))
  }

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T] =
    uniqueProviders.map(fn)
}
