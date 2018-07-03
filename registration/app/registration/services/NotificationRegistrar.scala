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

case class StoredRegistration(deviceId: String, platform: Platform, tagIds: Set[String], topics: Set[Topic])

object StoredRegistration {
  import play.api.libs.json._

  implicit val jf = Json.format[StoredRegistration]

  def fromRegistration(registration: Registration): StoredRegistration = {
    StoredRegistration(
      deviceId = registration.deviceId,
      platform = registration.platform,
      tagIds = registration.topics.map(_.id),
      topics = registration.topics
    )
  }
}


trait NotificationRegistrar {
  type RegistrarResponse[T] = Future[Either[ProviderError, T]]
  val providerIdentifier: String
  def register(oldDeviceId: String, registration: Registration): RegistrarResponse[RegistrationResponse]
  def unregister(pushToken: String): RegistrarResponse[Unit]
  def findRegistrations(topic: Topic, cursor: Option[String] = None): Future[Either[ProviderError, Paginated[StoredRegistration]]]
  def findRegistrations(pushToken: String): Future[Either[ProviderError, List[StoredRegistration]]]
}
