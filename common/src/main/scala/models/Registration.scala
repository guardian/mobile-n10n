package models

case class Registration(deviceId: String, platform: Platform, topics: Set[Topic], buildTier: Option[String])

object Registration {
  import play.api.libs.json._

  implicit val writes = Json.writes[Registration]

  implicit val reads = new Reads[Registration] {
    override def reads(json: JsValue): JsResult[Registration] =
      basicReader.reads(json)

    private val basicReader = Json.reads[Registration]

  }
}
