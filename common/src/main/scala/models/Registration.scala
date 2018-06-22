package models

case class Registration(deviceId: String, platform: Platform, topics: Set[Topic], buildTier: Option[String])

object Registration {
  import play.api.libs.json._

  implicit val writes = Json.writes[Registration]

  implicit val reads = Json.reads[Registration]

}
