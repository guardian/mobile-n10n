package azure.apns

import play.api.libs.json._

case class APS(
  alert: Option[Alert] = None,
  badge: Option[Int] = None,
  sound: Option[String] = None,
  `content-available`: Option[Int] = None,
  category: Option[String] = None
)
object APS {
  implicit val jf = Json.format[APS]
}