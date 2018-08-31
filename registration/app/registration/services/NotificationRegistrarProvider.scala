package registration.services

import error.{NotificationsError, RequestError}
import models._
import registration.services.azure._

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import cats.implicits._
import com.amazonaws.services.cloudwatch.model.StandardUnit
import metrics.{MetricDataPoint, Metrics}
import registration.services.fcm.FcmRegistrar

trait RegistrarProvider {
  def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar]

  def registrarFor(platform: Platform, deviceToken: DeviceToken): Either[NotificationsError, NotificationRegistrar]

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
    registrarFor(registration.platform, registration.deviceToken)

  override def registrarFor(platform: Platform, deviceToken: DeviceToken): Either[NotificationsError, NotificationRegistrar] = {
    platform match {
      case Android => Right(gcmRegistrar)
      case `iOS` => Right(apnsRegistrar)
      case Newsstand => Right(newsstandRegistrar)
      case _ => Left(UnsupportedPlatform(platform.toString))
    }
  }

  def withAllRegistrars[T](fn: (NotificationRegistrar => T)): List[T] =
    uniqueAzureProviders.map(fn)
}

class MigratingRegistrarProvider(
  standardRegistrarProvider: RegistrarProvider,
  fcmRegistrar: FcmRegistrar,
  metrics: Metrics
)(implicit executionContext: ExecutionContext) extends RegistrarProvider {
  override def registrarFor(platform: Platform, deviceToken: DeviceToken): Either[NotificationsError, NotificationRegistrar] = deviceToken match {
    case AzureToken(_) =>
      metrics.send(MetricDataPoint(name = "RegistrationAzure", value = 1d, unit = StandardUnit.Count))
      standardRegistrarProvider.registrarFor(platform, deviceToken)
    case FcmToken(_) =>
      metrics.send(MetricDataPoint(name = "RegistrationFcm", value = 1d, unit = StandardUnit.Count))
      Right(fcmRegistrar)
    case BothTokens(_, _) =>
      metrics.send(MetricDataPoint(name = "RegistrationBoth", value = 1d, unit = StandardUnit.Count))
      standardRegistrarProvider
        .registrarFor(platform, deviceToken)
        .map(legacyRegistrar => new MigratingRegistrar(fcmRegistrar, legacyRegistrar))
  }

  // delegate to the registrar provider
  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform, registration.deviceToken)

  // delegate to the registrar provider
  override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] =
    standardRegistrarProvider.withAllRegistrars(fn)
}