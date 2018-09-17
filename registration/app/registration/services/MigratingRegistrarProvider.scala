package registration.services

import com.amazonaws.services.cloudwatch.model.StandardUnit
import error.NotificationsError
import metrics.{MetricDataPoint, Metrics}
import models.Provider.{Azure, FCM}
import models._
import registration.services.fcm.FcmRegistrar

import scala.concurrent.ExecutionContext

class MigratingRegistrarProvider(
  azureRegistrarProvider: RegistrarProvider,
  fcmRegistrar: FcmRegistrar,
  metrics: Metrics
)(implicit executionContext: ExecutionContext) extends RegistrarProvider {
  override def registrarFor(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = deviceToken match {
    case AzureToken(_) =>
      metrics.send(MetricDataPoint(name = "RegistrationAzure", value = 1d, unit = StandardUnit.Count))
      azureRegistrarProvider.registrarFor(platform, deviceToken, currentProvider)
    case FcmToken(_) =>
      metrics.send(MetricDataPoint(name = "RegistrationFcm", value = 1d, unit = StandardUnit.Count))
      Right(fcmRegistrar)
    case BothTokens(_, _) if platform == Android =>
      metrics.send(MetricDataPoint(name = "RegistrationBoth", value = 1d, unit = StandardUnit.Count))
      androidMigration(platform, deviceToken, currentProvider)
    case BothTokens(_, _) if platform == iOS =>
      metrics.send(MetricDataPoint(name = "RegistrationBoth", value = 1d, unit = StandardUnit.Count))
      iosMigration(platform, deviceToken, currentProvider)
  }

  private def androidMigration(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = {
    azureRegistrarProvider
      .registrarFor(platform, deviceToken, Some(Azure))
      .map(azureRegistrar => new MigratingRegistrar("AzureToFirebaseRegistrar", fcmRegistrar, azureRegistrar))
  }

  private def iosMigration(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = {
    currentProvider match {
      case Some(FCM) =>
        azureRegistrarProvider
          .registrarFor(platform, deviceToken, Some(Azure))
          .map(azureRegistrar => new MigratingRegistrar("FirebaseToAzureRegistrar", azureRegistrar, fcmRegistrar))
      case _ => azureRegistrarProvider.registrarFor(platform, deviceToken, Some(Azure))
    }
  }

  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform, registration.deviceToken, registration.provider)

  override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] =
    azureRegistrarProvider.withAllRegistrars(fn)
}