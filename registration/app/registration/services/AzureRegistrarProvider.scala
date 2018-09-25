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



final class AzureRegistrarProvider(
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
    registrarFor(registration.platform, registration.deviceToken, registration.provider)

  override def registrarFor(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[RegistrationProvider]): Either[NotificationsError, NotificationRegistrar] = {
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

