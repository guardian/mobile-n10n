package com.gu.liveactivities.models

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.functional.syntax._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.gu.liveactivities.util.DateTimeHelper.{dateTimeFromString, dateTimeToString}

sealed trait LiveActivityData

case class FootballLiveActivity(
		homeTeam: String,
		awayTeam: String,
		articleUrl: String
) extends LiveActivityData

object FootballLiveActivity {
	implicit val format: OFormat[FootballLiveActivity] =
		Json.format[FootballLiveActivity]
}

object LiveActivityData {
	implicit val format: OFormat[LiveActivityData] =
		new OFormat[LiveActivityData] {
			def writes(data: LiveActivityData): JsObject = data match {
				case f: FootballLiveActivity =>
					FootballLiveActivity.format.writes(f) + ("type" -> JsString(
						"football"
					))
			}
			def reads(json: JsValue): JsResult[LiveActivityData] =
				(json \ "type").validate[String].flatMap {
					case "football" => FootballLiveActivity.format.reads(json)
					case other      => JsError(s"Unknown LiveActivityData type: $other")
				}
		}
}

case class LiveActivityMapping(
		id: String,
		channelId: String,
    isChannelActive: Boolean,
    isLive: Boolean,
		data: Option[LiveActivityData],
    competitionId: Option[String],
    lastEventId: Option[String],
    lastEventAt: Option[ZonedDateTime]
)
object LiveActivityMapping {
  implicit val reads: Reads[LiveActivityMapping] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "channelId").read[String] and
    (JsPath \ "isChannelActive").read[Boolean] and
    (JsPath \ "isLive").read[Boolean] and
    (JsPath \ "data").readNullable[LiveActivityData] and
    (JsPath \ "competitionId").readNullable[String] and
    (JsPath \ "lastEventId").readNullable[String] and
    (JsPath \ "lastEventAt").readNullable[String].map(_.map(dateTimeFromString))
  )(LiveActivityMapping.apply _)

	implicit val writes: OWrites[LiveActivityMapping] = (
    (JsPath \ "id").write[String] and
    (JsPath \ "channelId").write[String] and
    (JsPath \ "isChannelActive").write[Boolean] and
    (JsPath \ "isLive").write[Boolean] and
    (JsPath \ "data").writeNullable[LiveActivityData] and
    (JsPath \ "competitionId").writeNullable[String] and
    (JsPath \ "lastEventId").writeNullable[String] and
    (JsPath \ "lastEventAt").writeNullable[String]
  )(r => (r.id, r.channelId, r.isChannelActive, r.isLive, r.data, r.competitionId, r.lastEventId, r.lastEventAt.map(dateTimeToString)))
}