package registration.services

import models.{DeviceToken, Registration, Topic, UniqueDeviceIdentifier}
import models.pagination.Paginated
import cats.implicits._

import scala.concurrent.ExecutionContext
import NotificationRegistrar.RegistrarResponse
import cats.data.EitherT

class MigratingRegistrar(
  val providerIdentifier: String,
  fromRegistrar: NotificationRegistrar,
  toRegistrar: NotificationRegistrar
)(implicit ec: ExecutionContext) extends NotificationRegistrar {

  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = {
    for {
      _ <- fromRegistrar.unregister(deviceToken)
      result <- toRegistrar.register(deviceToken, registration)
    } yield result
  }

  override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = {
    val response = for {
      legacyResponse <- fromRegistrar.unregister(deviceToken)
      fcmResponse <- toRegistrar.unregister(deviceToken)
    } yield fcmResponse

    response
  }

  override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] =
    fromRegistrar.findRegistrations(topic, cursor)

  override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = {
    val registrations = for {
      fcmRegistrations <- EitherT(toRegistrar.findRegistrations(deviceToken))
      legacyRegistrations <- EitherT(fromRegistrar.findRegistrations(deviceToken))
    } yield fcmRegistrations ++ legacyRegistrations

    registrations.value
  }

  override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] =
    fromRegistrar.findRegistrations(udid)
}
