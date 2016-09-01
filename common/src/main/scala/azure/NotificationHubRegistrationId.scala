package azure


import play.api.libs.json.Json

import cats.data.Xor

case class NotificationHubRegistrationId(registrationId: String)

object NotificationHubRegistrationId {
  implicit val jf = Json.format[NotificationHubRegistrationId]

  def fromString(registrationId: String): String Xor NotificationHubRegistrationId =
    Xor.right(NotificationHubRegistrationId(registrationId))
}