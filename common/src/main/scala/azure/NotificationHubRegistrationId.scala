package azure

import play.api.libs.json.Json

import scalaz.\/
import scalaz.syntax.either._

case class NotificationHubRegistrationId(registrationId: String)

object NotificationHubRegistrationId {
  implicit val jf = Json.format[NotificationHubRegistrationId]

  def fromString(registrationId: String): String \/ NotificationHubRegistrationId =
    NotificationHubRegistrationId(registrationId).right
}