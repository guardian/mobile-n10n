package azure.apns

import play.api.libs.json.Json

case class ElectionProperties(
  title: String,
  body: String,
  richviewbody: String,
  sound: Int,
  dem: Int,
  rep: Int,
  link: String,
  results: String
)
object ElectionProperties {
  implicit val jf = Json.format[ElectionProperties]
}