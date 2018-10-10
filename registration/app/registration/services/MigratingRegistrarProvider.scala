package registration.services

import com.amazonaws.services.cloudwatch.model.StandardUnit
import error.NotificationsError
import metrics.{MetricDataPoint, Metrics}
import models.RegistrationProvider.{Azure, AzureWithFirebase, FCM}
import models.{IOS, Android, Platform, DeviceToken, RegistrationProvider, Registration}
import registration.services.fcm.FcmRegistrar

import scala.concurrent.ExecutionContext

class MigratingRegistrarProvider(
  azureRegistrarProvider: RegistrarProvider,
  fcmRegistrar: NotificationRegistrar,
  azureWithFirebaseRegistrar: NotificationRegistrar,
  metrics: Metrics
)(implicit executionContext: ExecutionContext) extends RegistrarProvider {
  override def registrarFor(
    platform: Platform,
    deviceToken: DeviceToken,
    currentProvider: Option[RegistrationProvider]
  ): Either[NotificationsError, NotificationRegistrar] = {
    platform match {
      case IOS => iosMigration(platform, deviceToken, currentProvider)
      case Android => androidMigration(platform, deviceToken, currentProvider)
      case _ if deviceToken.hasAzureToken =>
        metrics.send(MetricDataPoint(name = "RegistrationAzure", value = 1d, unit = StandardUnit.Count))
        azureRegistrarProvider.registrarFor(platform, deviceToken, currentProvider)
      case _ if deviceToken.hasFcmToken =>
        metrics.send(MetricDataPoint(name = "RegistrationFcm", value = 1d, unit = StandardUnit.Count))
        Right(fcmRegistrar)
    }
  }

  private def androidMigration(
    platform: Platform,
    deviceToken: DeviceToken,
    currentProvider: Option[RegistrationProvider]
  ): Either[NotificationsError, NotificationRegistrar] = {
    def azureOnlyRegistrar: Either[NotificationsError, NotificationRegistrar] =
      azureRegistrarProvider.registrarFor(platform, deviceToken, Some(Azure))

    currentProvider match {
      // Azure => Azure with Firebase
      case Some(Azure) if deviceToken.hasFcmToken =>
        metrics.send(MetricDataPoint(name = "AndroidAzureToAzureWithFirebase", value = 1d, unit = StandardUnit.Count))
        azureOnlyRegistrar.map(azureOnly => new MigratingRegistrar(
          providerIdentifier = "AzureToAzureWithFirebaseRegistrar",
          fromRegistrar = azureOnly,
          toRegistrar = azureWithFirebaseRegistrar
        ))
      // Azure with Firebase
      case Some(AzureWithFirebase) if deviceToken.hasFcmToken =>
        metrics.send(MetricDataPoint(name = "AndroidAzureWithFirebase", value = 1d, unit = StandardUnit.Count))
        Right(azureWithFirebaseRegistrar)
      // Firebase => Azure with Firebase
      case _ if deviceToken.hasFcmToken =>
        metrics.send(MetricDataPoint(name = "AndroidFirebaseToAzureWithFirebase", value = 1d, unit = StandardUnit.Count))
        Right(new MigratingRegistrar(
          providerIdentifier = "FirebaseToAzureWithFirebaseRegistrar",
          fromRegistrar = fcmRegistrar,
          toRegistrar = azureWithFirebaseRegistrar
        ))
      // Azure only
      case _ if deviceToken.hasAzureToken =>
        metrics.send(MetricDataPoint(name = "AndroidAzure", value = 1d, unit = StandardUnit.Count))
        azureOnlyRegistrar
    }
  }

  private def iosMigration(
    platform: Platform,
    deviceToken: DeviceToken,
    currentProvider: Option[RegistrationProvider]
  ): Either[NotificationsError, NotificationRegistrar] = {
    currentProvider match {
      case Some(FCM) if deviceToken.hasAzureToken && deviceToken.hasFcmToken =>
        metrics.send(MetricDataPoint(name = "IosFcmToAzure", value = 1d, unit = StandardUnit.Count))
        azureRegistrarProvider
          .registrarFor(platform, deviceToken, Some(Azure))
          .map(azureRegistrar => new MigratingRegistrar(
            providerIdentifier = "FirebaseToAzureRegistrar",
            fromRegistrar = fcmRegistrar,
            toRegistrar = azureRegistrar
          ))
      case _ if deviceToken.hasAzureToken =>
        metrics.send(MetricDataPoint(name = "RegistrationAzure", value = 1d, unit = StandardUnit.Count))
        azureRegistrarProvider.registrarFor(platform, deviceToken, Some(Azure))
      case _ if deviceToken.hasFcmToken =>
        metrics.send(MetricDataPoint(name = "RegistrationFcm", value = 1d, unit = StandardUnit.Count))
        Right(fcmRegistrar)
    }
  }

  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform, registration.deviceToken, registration.provider)

  override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] =
    azureRegistrarProvider.withAllRegistrars(fn)
}