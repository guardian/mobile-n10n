package registration.services

import models._
import providers.ProviderError

import scala.concurrent.Future
import models.pagination.Paginated

case class RegistrationResponse(deviceId: String, platform: Platform, userId: UniqueDeviceIdentifier, topics: Set[Topic])

object RegistrationResponse {
  import play.api.libs.json._

  implicit val jf = Json.format[RegistrationResponse]
}

case class StoredRegistration(deviceId: String, platform: Platform, userId: Option[UniqueDeviceIdentifier], tagIds: Set[String], topics: Set[Topic])

object StoredRegistration {
  import play.api.libs.json._

  implicit val jf = Json.format[StoredRegistration]

  def fromRegistration(registration: Registration): StoredRegistration = {
    StoredRegistration(
      deviceId = registration.deviceId,
      platform = registration.platform,
      userId = Some(registration.udid),
      tagIds = registration.topics.map(_.id),
      topics = registration.topics
    )
  }
}


trait NotificationRegistrar {
  type RegistrarResponse[T] = Future[Either[ProviderError, T]]
  val providerIdentifier: String
  def register(oldDeviceId: String, registration: Registration): RegistrarResponse[RegistrationResponse]
  def unregister(udid: UniqueDeviceIdentifier): RegistrarResponse[Unit]
  def findRegistrations(topic: Topic, cursor: Option[String] = None): Future[Either[ProviderError, Paginated[StoredRegistration]]]
  def findRegistrations(lastKnownChannelUri: String): Future[Either[ProviderError, List[StoredRegistration]]]
  def findRegistrations(udid: UniqueDeviceIdentifier): Future[Either[ProviderError, Paginated[StoredRegistration]]]
}
