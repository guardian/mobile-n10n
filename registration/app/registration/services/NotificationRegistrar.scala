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

  implicit val jf = Json.format[RegistrationResponse]
}

trait NotificationRegistrar {
  import NotificationRegistrar.RegistrarResponse
  val providerIdentifier: String
  def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse]
  def unregister(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[Unit]
}

object NotificationRegistrar {
  type RegistrarResponse[T] = Future[Either[ProviderError, T]]
}
