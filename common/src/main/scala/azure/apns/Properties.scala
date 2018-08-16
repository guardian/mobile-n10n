package azure.apns

import java.util.UUID

import play.api.libs.json.{JsResult, Reads, _}
import models.NotificationType

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
  uniqueIdentifier: UUID,
  provider: String,
  notificationType: NotificationType,
  election: Option[ElectionProperties] = None,
  liveEvent: Option[LiveEventProperties] = None,
  footballMatch: Option[FootballMatchStatusProperties] = None
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