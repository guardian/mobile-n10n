package registration.services

import com.amazonaws.services.cloudwatch.model.StandardUnit
import error.NotificationsError
import metrics.{MetricDataPoint, Metrics}
import models._
import registration.services.fcm.FcmRegistrar

import scala.concurrent.ExecutionContext

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
        .map(legacyRegistrar => new MigratingRegistrar("AzureToFirebaseRegistrar", fcmRegistrar, legacyRegistrar))
  }

  // delegate to the registrar provider
  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform, registration.deviceToken)

  // delegate to the registrar provider
  override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] =
    standardRegistrarProvider.withAllRegistrars(fn)
}