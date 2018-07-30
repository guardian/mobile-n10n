package registration.services

import error.{NotificationsError, RequestError}
import models._
import registration.services.azure._

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import cats.implicits._
import registration.services.fcm.FcmRegistrar

trait RegistrarProvider {
  def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar]

  def registrarFor(platform: Platform): Either[NotificationsError, NotificationRegistrar]

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T]
}

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

case class MalformattedRegistration(description: String) extends RequestError {
  override def reason: String = s"Malformatred request: $reason"
}

final class NotificationRegistrarProvider(
  gcmRegistrar: GCMNotificationRegistrar,
  apnsRegistrar: APNSNotificationRegistrar,
  newsstandRegistrar: NewsstandNotificationRegistrar)
  (implicit executionContext: ExecutionContext) extends RegistrarProvider {

  private val azureBasedRegistrars = List(gcmRegistrar, apnsRegistrar, newsstandRegistrar)
  private val uniqueAzureProviders: List[NotificationRegistrar] =
    azureBasedRegistrars
      .groupBy(_.hubClient.notificationHubConnection)
      .values
      .flatMap(_.headOption)(breakOut)


  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform)

  override def registrarFor(platform: Platform): Either[NotificationsError, NotificationRegistrar] = platform match {
    case Android => Right(gcmRegistrar)
    case `iOS` => Right(apnsRegistrar)
    case Newsstand => Right(newsstandRegistrar)
    case _ => Left(UnsupportedPlatform(platform.toString))
  }

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T] =
    uniqueAzureProviders.map(fn)
}


class MigratingRegistrarProvider(
  standardRegistrarProvider: RegistrarProvider,
  fcmRegistrar: FcmRegistrar
)(implicit executionContext: ExecutionContext) extends RegistrarProvider {
  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] = registration.deviceToken match {
    case AzureToken(_) => standardRegistrarProvider.registrarFor(registration)
    case FcmToken(_) => Right(fcmRegistrar)
    case BothTokens(_, _) =>
      standardRegistrarProvider
        .registrarFor(registration)
        .map(legacyRegistrar => new MigratingRegistrar(fcmRegistrar, legacyRegistrar))
  }

  // delegate to the registrar provider
  override def registrarFor(platform: Platform): Either[NotificationsError, NotificationRegistrar] =
    standardRegistrarProvider.registrarFor(platform)

  // delegate to the registrar provider
  override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] =
    standardRegistrarProvider.withAllRegistrars(fn)
}