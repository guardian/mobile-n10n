package registration.services

import models._
import providers.ProviderError

import scala.concurrent.Future
import models.pagination.Paginated

case class RegistrationResponse(deviceId: String, platform: Platform, topics: Set[Topic])

object RegistrationResponse {
  import play.api.libs.json._

  implicit val jf = Json.format[RegistrationResponse]
}

case class StoredRegistration(
  deviceId: String,
  platform: Platform,
  tagIds: Set[String],
  topics: Set[Topic],
  provider: String
)

object StoredRegistration {
  import play.api.libs.json._

  implicit val jf = Json.format[StoredRegistration]

  def fromRegistration(registration: Registration): StoredRegistration = {
    StoredRegistration(
      deviceId = registration.deviceToken.azureToken,
      platform = registration.platform,
      tagIds = registration.topics.map(_.id),
      topics = registration.topics,
      provider = "unknown"
    )
  }
}


trait NotificationRegistrar {
  import NotificationRegistrar.RegistrarResponse
  val providerIdentifier: String
  def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse]
  def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit]
  def findRegistrations(topic: Topic, cursor: Option[String] = None): RegistrarResponse[Paginated[StoredRegistration]]
  def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]]
  def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]]
}

object NotificationRegistrar {
  type RegistrarResponse[T] = Future[Either[ProviderError, T]]
}
