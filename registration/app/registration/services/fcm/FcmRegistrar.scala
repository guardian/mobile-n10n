package registration.services.fcm

import models.pagination.Paginated
import models._
import providers.ProviderError
import registration.services.NotificationRegistrar.RegistrarResponse
import registration.services.{NotificationRegistrar, RegistrationResponse, StoredRegistration}

import scala.concurrent.Future

class FcmRegistrar extends NotificationRegistrar {
  override val providerIdentifier: String = "FCM"
  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = ???
  override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = ???
  override def findRegistrations(topic: Topic, cursor: Option[String]): Future[Either[ProviderError, Paginated[StoredRegistration]]] = ???
  override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = ???
  override def findRegistrations(udid: UniqueDeviceIdentifier): Future[Either[ProviderError, Paginated[StoredRegistration]]] = ???
}
