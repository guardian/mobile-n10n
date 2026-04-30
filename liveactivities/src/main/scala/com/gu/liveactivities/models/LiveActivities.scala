package com.gu.liveactivities.models

import com.gu.mobile.notifications.client.models.liveActitivites.{ContentState, FootballMatchContentState}
import org.scanamo.DynamoFormat
import org.scanamo.generic.semiauto._
import play.api.libs.json.Reads._
import play.api.libs.json._

import java.time.ZonedDateTime

sealed trait LiveActivityData

case class FootballLiveActivity(
    homeTeam: String,
    awayTeam: String,
    competitionId: String,
    kickOffTimestamp: Long
) extends LiveActivityData

object FootballLiveActivity {
  implicit val format: OFormat[FootballLiveActivity] =
    Json.format[FootballLiveActivity]

  implicit val dynamoFormat: DynamoFormat[FootballLiveActivity] = deriveDynamoFormat[FootballLiveActivity]
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

  implicit val dynamoFormat: DynamoFormat[LiveActivityData] = deriveDynamoFormat[LiveActivityData]
  def toLiveActivityData(contentState: ContentState): LiveActivityData = contentState match {
    case footballMatchContentState: FootballMatchContentState =>
      FootballLiveActivity(
        homeTeam = footballMatchContentState.homeTeam.name,
        awayTeam = footballMatchContentState.awayTeam.name,
        competitionId = footballMatchContentState.competition.name,
        kickOffTimestamp = footballMatchContentState.kickOffTimestamp
      )
  }
}

case class LiveActivityMapping(
    id: String,
    channelId: String,
    isChannelActive: Boolean,
    isLive: Boolean,
    data: Option[LiveActivityData],
    lastEventId: Option[String],
    lastEventAt: Option[ZonedDateTime],
		createdAt: ZonedDateTime,
    lastModifiedAt: ZonedDateTime
)

object LiveActivityMapping {
  import com.gu.liveactivities.util.DateTimeHelper.zonedDateTimeJsonFormat

  import com.gu.liveactivities.util.DateTimeHelper.zonedDateTimeDynamoFormat

  implicit val dynamoFormat: DynamoFormat[LiveActivityMapping] = deriveDynamoFormat[LiveActivityMapping]
  
  implicit val format: Format[LiveActivityMapping] = Json.format[LiveActivityMapping]
}