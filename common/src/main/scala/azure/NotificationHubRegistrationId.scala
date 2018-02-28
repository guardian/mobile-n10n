package azure


import play.api.libs.json.Json

case class NotificationHubRegistrationId(registrationId: String)

object NotificationHubRegistrationId {
  implicit val jf = Json.format[NotificationHubRegistrationId]

  def fromString(registrationId: String): Either[String, NotificationHubRegistrationId] =
    Right(NotificationHubRegistrationId(registrationId))
}