package azure.apns

import play.api.libs.json.{JsResult, Reads, _}

sealed trait Properties

case class LegacyProperties(underlying: Map[String, String]) extends Properties

object LegacyProperties {
  implicit val jf = new OFormat[LegacyProperties] {
    override def writes(o: LegacyProperties): JsObject =
      implicitly[OWrites[Map[String, String]]].writes(o.underlying)

    override def reads(json: JsValue): JsResult[LegacyProperties] =
      implicitly[Reads[Map[String, String]]].reads(json).map(LegacyProperties.apply)
  }
}

case class StandardProperties(
  t: String,
  election: Option[ElectionProperties] = None,
  liveEvent: Option[LiveEventProperties] = None
) extends Properties

object StandardProperties {
  implicit val jf = Json.format[StandardProperties]
}

object Properties {
  implicit val jf = new OFormat[Properties] {
    override def writes(o: Properties): JsObject = o match {
      case obj: LegacyProperties => implicitly[OWrites[LegacyProperties]].writes(obj)
      case obj: StandardProperties => implicitly[OWrites[StandardProperties]].writes(obj)
    }

    override def reads(json: JsValue): JsResult[Properties] = {
      implicitly[Reads[LegacyProperties]].reads(json)
    } orElse {
      implicitly[Reads[StandardProperties]].reads(json)
    }
  }
}