package azure.apns

import play.api.libs.json._

case class BodyWithoutCustom(aps: APS)
object BodyWithoutCustom {
  implicit val jf = Json.format[BodyWithoutCustom]
}