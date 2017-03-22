package azure.apns

import play.api.libs.json._

case class Body(aps: APS, customProperties: Properties)
object Body {
  implicit val jf = new Format[Body] {
    override def writes(o: Body): JsValue =
      Properties.jf.writes(o.customProperties) + ("aps" -> Json.toJson(o.aps))

    override def reads(json: JsValue): JsResult[Body] = for {
      aps <- (json \ "aps").validate[APS]
      bodyRaw <- json.validate[Properties]
    } yield Body(
      aps = aps,
      customProperties = bodyRaw
    )
  }
}