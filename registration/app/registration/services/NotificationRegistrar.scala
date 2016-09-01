package registration.services

import models.{Platform, Registration, Topic, UniqueDeviceIdentifier}
import providers.ProviderError

import scala.concurrent.Future
import cats.data.Xor

case class RegistrationResponse(deviceId: String, platform: Platform, userId: UniqueDeviceIdentifier, topics: Set[Topic])

object RegistrationResponse {
  import play.api.libs.json._

  implicit val jf = Json.format[RegistrationResponse]
}

trait NotificationRegistrar {
  type RegistrarResponse = Future[ProviderError Xor RegistrationResponse]
  def register(oldDeviceId: String, registration: Registration): RegistrarResponse
}
