package registration.models

import play.api.libs.json.Json

case class LegacyNewsstandRegistration(pushToken: String)

object LegacyNewsstandRegistration {
  implicit val jf = Json.format[LegacyNewsstandRegistration]
}
