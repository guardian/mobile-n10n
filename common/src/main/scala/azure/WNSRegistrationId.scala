package azure

import play.api.libs.json.Json

import scalaz.\/
import scalaz.syntax.either._

case class WNSRegistrationId(registrationId: String)

object WNSRegistrationId {
  implicit val jf = Json.format[WNSRegistrationId]

  def fromString(registrationId: String): String \/ WNSRegistrationId =
    WNSRegistrationId(registrationId).right
}