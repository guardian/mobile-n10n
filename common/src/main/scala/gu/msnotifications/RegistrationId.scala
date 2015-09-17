package gu.msnotifications

import play.api.libs.json.Json

import scalaz.\/

case class RegistrationId(registrationId: String)

object RegistrationId {
  implicit val jf = Json.format[RegistrationId]

  def fromString(registrationId: String): String \/ RegistrationId =
    \/.right(RegistrationId(registrationId))
}