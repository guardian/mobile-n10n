package azure.apns

import play.api.libs.json._

import models.JsonUtils._

case class APS(
  alert: Option[Either[Alert, String]] = None,
  badge: Option[Int] = None,
  sound: Option[String] = None,
  `content-available`: Option[Int] = None,
  category: Option[String] = None,
  `mutable-content`: Option[Int] = None
)
object APS {
  implicit val jf = Json.format[APS]
}