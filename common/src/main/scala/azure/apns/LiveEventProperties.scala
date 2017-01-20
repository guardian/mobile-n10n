package azure.apns

import play.api.libs.json.Json

case class LiveEventProperties(
  title: String,
  body: String,
  richviewbody: String,
  sound: Int,
  link1: String,
  link2: String,
  imageURL: Option[String],
  topics: String
)
object LiveEventProperties {
  implicit val jf = Json.format[LiveEventProperties]
}