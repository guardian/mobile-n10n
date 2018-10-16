package registration.services

import error.NotificationsError
import metrics.Metrics
import models.{DeviceToken, Platform, Provider, Registration}

import scala.concurrent.ExecutionContext

class CopyingRegistrarProvider(
  delegateRegistrarProvider: RegistrarProvider,
  copyRegistrar: NotificationRegistrar,
  metrics: Metrics
)(implicit executionContext: ExecutionContext) extends RegistrarProvider {

  override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] =
    registrarFor(registration.platform, registration.deviceToken, registration.provider)

  override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] =
    delegateRegistrarProvider.withAllRegistrars(fn)

  override def registrarFor(
    platform: Platform,
    deviceToken: DeviceToken,
    currentProvider: Option[Provider]
  ): Either[NotificationsError, NotificationRegistrar] = {

    def wrapWithCopyRegistrar(registrar: NotificationRegistrar): NotificationRegistrar = {
      new CopyingRegistrar(
        providerIdentifier = "CopyingRegistrar",
        mainRegistrar = registrar,
        copyRegistrar = copyRegistrar
      )
    }

    delegateRegistrarProvider
      .registrarFor(platform, deviceToken, currentProvider)
      .map(wrapWithCopyRegistrar)
  }
}
