package com.gu.liveactivities.models

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.functional.syntax._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.gu.liveactivities.util.DateTimeHelper.{dateTimeFromString, dateTimeToString}
import org.scanamo.DynamoFormat
import org.scanamo.generic.semiauto._

sealed trait LiveActivityData

case class FootballLiveActivity(
    homeTeam: String,
    awayTeam: String,
    articleUrl: String
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
}

case class LiveActivityMapping(
    id: String,
    channelId: String,
    isChannelActive: Boolean,
    isLive: Boolean,
    data: Option[LiveActivityData],
    competitionId: Option[String],
    lastEventId: Option[String],
    lastEventAt: Option[ZonedDateTime],
		createdAt: ZonedDateTime,
    lastModifiedAt: ZonedDateTime
)

object LiveActivityMapping {

  import com.gu.liveactivities.util.DateTimeHelper.zonedDateTimeFormat

  implicit val zonedDateTimeFormat: DynamoFormat[ZonedDateTime] = DynamoFormat.coercedXmap[ZonedDateTime, String, IllegalArgumentException](
    dateTimeFromString,
    dateTimeToString
  )

  implicit val dynamoFormat: DynamoFormat[LiveActivityMapping] = deriveDynamoFormat[LiveActivityMapping]
  
  implicit val format: Format[LiveActivityMapping] = Json.format[LiveActivityMapping]
}