package models

case class Registration(deviceId: String, platform: Platform, udid: UniqueDeviceIdentifier, topics: Set[Topic])

object Registration {
  import play.api.libs.json._

  implicit val writes = Json.writes[Registration]

  implicit val reads = new Reads[Registration] {
    override def reads(json: JsValue): JsResult[Registration] =
      replaceUserId(json).flatMap(basicReader.reads)

    private val basicReader = Json.reads[Registration]

    private def replaceUserId(json: JsValue) = json.validate[JsObject] map { o =>
      (o.value.get("udid"), o.value.get("userId")) match {
        case (None, Some(userId)) => o + ("udid" -> userId)
        case _ => o
      }
    }
  }
}
