package registration.models

import play.api.libs.json.{Json, OFormat}

case class LegacyNewsstandRegistration(pushToken: String)

object LegacyNewsstandRegistration {
  implicit val jf: OFormat[LegacyNewsstandRegistration] = Json.format[LegacyNewsstandRegistration]
}
