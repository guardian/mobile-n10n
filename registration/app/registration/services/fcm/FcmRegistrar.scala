package registration.services.fcm

import error.NotificationsError
import models.pagination.Paginated
import models.{Platform, Registration, Topic, UniqueDeviceIdentifier}
import providers.ProviderError
import registration.services.{NotificationRegistrar, RegistrarProvider, RegistrationResponse, StoredRegistration}

import scala.concurrent.Future

class FcmRegistrar extends NotificationRegistrar {
  override val providerIdentifier: String = "FCM"
  override def register(oldDeviceId: String, registration: Registration): RegistrarResponse[RegistrationResponse] = ???
  override def unregister(pushToken: String): RegistrarResponse[Unit] = ???
  override def findRegistrations(topic: Topic, cursor: Option[String]): Future[Either[ProviderError, Paginated[StoredRegistration]]] = ???
  override def findRegistrations(pushToken: String): Future[Either[ProviderError, List[StoredRegistration]]] = ???
  override def findRegistrations(udid: UniqueDeviceIdentifier): Future[Either[ProviderError, Paginated[StoredRegistration]]] = ???
}
