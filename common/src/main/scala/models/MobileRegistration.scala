package models

case class MobileRegistration(deviceId: String, platform: Platform, userId: UserId)

object MobileRegistration {
  import play.api.libs.json._

  implicit val jf = Json.format[MobileRegistration]
}
