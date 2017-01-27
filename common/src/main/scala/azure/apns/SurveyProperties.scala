package azure.apns

import play.api.libs.json.Json

case class SurveyProperties(
  title: String,
  body: String,
  sound: Int,
  link: String,
  imageURL: Option[String],
  topics: String
)
object SurveyProperties {
  implicit val jf = Json.format[SurveyProperties]
}