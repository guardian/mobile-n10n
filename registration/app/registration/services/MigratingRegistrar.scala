package registration.services

import models.{DeviceToken, Registration, Topic, UniqueDeviceIdentifier}
import models.pagination.Paginated
import registration.services.fcm.FcmRegistrar
import cats.implicits._

import scala.concurrent.ExecutionContext
import NotificationRegistrar.RegistrarResponse
import cats.data.EitherT

class MigratingRegistrar(
  fcmRegistrar: FcmRegistrar,
  legacyRegistrar: NotificationRegistrar
)(implicit ec: ExecutionContext) extends NotificationRegistrar {
  override val providerIdentifier: String = "AzureToFirebaseRegistrar"

  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = {
    for {
      _ <- legacyRegistrar.unregister(deviceToken)
      result <- fcmRegistrar.register(deviceToken, registration)
    } yield result
  }

  override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = {
    val response = for {
      legacyResponse <- legacyRegistrar.unregister(deviceToken)
      fcmResponse <- fcmRegistrar.unregister(deviceToken)
    } yield fcmResponse

    response
  }

  override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] =
    legacyRegistrar.findRegistrations(topic, cursor)

  override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = {
    val registrations = for {
      fcmRegistrations <- EitherT(fcmRegistrar.findRegistrations(deviceToken))
      legacyRegistrations <- EitherT(legacyRegistrar.findRegistrations(deviceToken))
    } yield fcmRegistrations ++ legacyRegistrations

    registrations.value
  }

  override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] =
    legacyRegistrar.findRegistrations(udid)
}
