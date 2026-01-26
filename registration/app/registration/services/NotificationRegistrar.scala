package registration.services

import models._
import providers.ProviderError

import scala.concurrent.Future

case class RegistrationResponse(
  deviceId: String,
  platform: Platform,
  topics: Set[Topic],
  provider: Provider
)

object RegistrationResponse {
  import play.api.libs.json._

  implicit val jf: OFormat[RegistrationResponse] = Json.format[RegistrationResponse]
}

trait NotificationRegistrar {
  import NotificationRegistrar.RegistrarResponse
  val providerIdentifier: String
  def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse]
  def dbHealthCheck(): Future[List[TopicCount]]
}

object NotificationRegistrar {
  type RegistrarResponse[T] = Future[Either[ProviderError, T]]
}
