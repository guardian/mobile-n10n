package models

case class Registration(deviceId: String, platform: Platform, userId: UniqueDeviceIdentifier, topics: Set[Topic])

object Registration {
  import play.api.libs.json._

  implicit val jf = Json.format[Registration]
}
