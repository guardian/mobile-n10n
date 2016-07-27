package azure.apns

import play.api.libs.json._

import scala.collection.breakOut

case class Body(aps: APS, customProperties: Map[String, String])
object Body {
  implicit val jf = new Format[Body] {
    override def writes(o: Body): JsValue =
      Json.toJson(o.customProperties.mapValues(JsString(_)) + ("aps" -> Json.toJson(o.aps)))

    override def reads(json: JsValue): JsResult[Body] = for {
      bodyWithoutCustom <- Json.fromJson[BodyWithoutCustom](json)
      bodyRaw <- Json.fromJson[JsObject](json)
    } yield Body(
      aps = bodyWithoutCustom.aps,
      customProperties = bodyRaw.value.filterKeys(_ != "aps").collect({
        case (k, JsString(v)) => k -> v
      })(breakOut)
    )
  }
}