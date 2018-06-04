package azure.apns

import play.api.libs.json._

case class Alert(
  title: Option[String] = None,
  body: Option[String] = None
)
object Alert {
  implicit val jf = Json.format[Alert]
}