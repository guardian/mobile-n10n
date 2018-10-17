package registration.services

import models._
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
      _ <- fromRegistrar.unregister(deviceToken, registration.platform)
      result <- toRegistrar.register(deviceToken, registration)
    } yield result
  }

  override def unregister(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[Unit] = {
    val response = for {
      fromResponse <- fromRegistrar.unregister(deviceToken, platform)
      toResponse <- toRegistrar.unregister(deviceToken, platform)
    } yield toResponse

    response
  }

  override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] =
    fromRegistrar.findRegistrations(topic, cursor)

  override def findRegistrations(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[List[StoredRegistration]] = {
    for {
      fromRegistrations <- EitherT(fromRegistrar.findRegistrations(deviceToken, platform)).getOrElse(Nil)
      toRegistrations <- EitherT(toRegistrar.findRegistrations(deviceToken, platform)).getOrElse(Nil)
    } yield Right(toRegistrations ++ fromRegistrations)
  }

  override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] =
    fromRegistrar.findRegistrations(udid)
}
