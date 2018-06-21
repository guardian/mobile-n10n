package azure.apns

import play.api.libs.json._

case class Alert(
  title: Option[String] = None,
  body: Option[String] = None,
  `title-loc-args`: Option[List[String]] = None,
  `action-loc-key`: Option[String] = None,
  `loc-key`: Option[String] = None,
  `loc-args`: Option[List[String]] = None,
  `launch-image`: Option[String] = None
)
object Alert {
  implicit val jf = Json.format[Alert]
}