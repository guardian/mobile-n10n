package com.gu.liveactivities.models

import play.api.libs.json._
import java.time.ZonedDateTime

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
    isEventLive: Boolean,
		eventData: Option[LiveActivityData],
    competitionId: Option[String],
    lastEventId: Option[String],
    lastEventUpdate: Option[ZonedDateTime]
)
object LiveActivityMapping {
	implicit val format: OFormat[LiveActivityMapping] =
		Json.format[LiveActivityMapping]
}