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
  fcmRegistrar: NotificationRegistrar,
  metrics: Metrics
)(implicit executionContext: ExecutionContext) extends RegistrarProvider {
  override def registrarFor(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = deviceToken match {
    case AzureToken(_) =>
      metrics.send(MetricDataPoint(name = "RegistrationAzure", value = 1d, unit = StandardUnit.Count))
      azureRegistrarProvider.registrarFor(platform, deviceToken, currentProvider)
    case FcmToken(_) if platform == iOS =>
      metrics.send(MetricDataPoint(name = "RegistrationFcm", value = 1d, unit = StandardUnit.Count))
      Right(fcmRegistrar)
    case FcmToken(_) if platform == Android =>
      androidMigration(platform, deviceToken, currentProvider)
    case BothTokens(_, _) if platform == Android =>
      androidMigration(platform, deviceToken, currentProvider)
    case BothTokens(_, _) if platform == iOS =>
      iosMigration(platform, deviceToken, currentProvider)
  }

  private def androidMigration(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = {
    metrics.send(MetricDataPoint(name = "AndroidRegistrationFirebaseToAzure", value = 1d, unit = StandardUnit.Count))
    azureRegistrarProvider
      .registrarFor(platform, deviceToken, Some(Azure))
      .map(azureRegistrar => new MigratingRegistrar(
        providerIdentifier = "AndroidFirebaseToAzureRegistrar",
        fromRegistrar = fcmRegistrar,
        toRegistrar = azureRegistrar
      ))
  }

  private def iosMigration(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = {
    currentProvider match {
      case Some(FCM) =>
        metrics.send(MetricDataPoint(name = "IosFcmToAzure", value = 1d, unit = StandardUnit.Count))
        azureRegistrarProvider
          .registrarFor(platform, deviceToken, Some(Azure))
          .map(azureRegistrar => new MigratingRegistrar(
            providerIdentifier = "IosFirebaseToAzureRegistrar",
            fromRegistrar = fcmRegistrar,
            toRegistrar = azureRegistrar
          ))
      case _ =>
        metrics.send(MetricDataPoint(name = "RegistrationAzure", value = 1d, unit = StandardUnit.Count))
        azureRegistrarProvider.registrarFor(platform, deviceToken, Some(Azure))
    }
  }

  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform, registration.deviceToken, registration.provider)

  override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] =
    azureRegistrarProvider.withAllRegistrars(fn)
}