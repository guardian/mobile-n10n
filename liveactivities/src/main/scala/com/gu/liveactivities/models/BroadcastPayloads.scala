package com.gu.liveactivities.models

import play.api.libs.json._

// GENERIC CONTENT STATE //////////////////////////////////////////////////

sealed trait ContentState
object ContentState {
  import FootballContentJsonFormats._

  implicit val format: OFormat[ContentState] = new OFormat[ContentState] {
    def writes(cs: ContentState): JsObject = cs match {
      case f: FootballMatchContentState =>
        footballMatchContentStateFormat
          .writes(f)
          .as[JsObject] + ("type" -> JsString("football"))
      // Add cases for other ContentState subtypes here
    }

    def reads(json: JsValue): JsResult[ContentState] = {
      (json \ "type").validate[String].flatMap {
        case "football" => footballMatchContentStateFormat.reads(json)
        // Add cases for other ContentState subtypes here
        case other => JsError(s"Unknown ContentState type: $other")
      }
    }
  }
}

// FOOTBALL CONTENT STATE //////////////////////////////////////////////////
// @formatter:off
sealed trait MatchStatus { val status: String }
case object Scheduled extends MatchStatus { val status = "SCHEDULED" }
case object PreMatch extends MatchStatus { val status = "PRE_MATCH" }
case object FirstHalf extends MatchStatus { val status = "FIRST_HALF" }
case object HalfTime extends MatchStatus { val status = "HALF_TIME" }
case object SecondHalf extends MatchStatus { val status = "SECOND_HALF" }
case object ExtraTimeFirstHalf extends MatchStatus { val status = "EXTRA_TIME_FIRST_HALF" }
case object ExtraTimeHalfTime extends MatchStatus { val status = "EXTRA_TIME_HALF_TIME" }
case object ExtraTimeSecondHalf extends MatchStatus { val status = "EXTRA_TIME_SECOND_HALF" }
case object Penalties extends MatchStatus { val status = "PENALTIES" }
case object FullTime extends MatchStatus { val status = "FULL_TIME" }
case object Postponed extends MatchStatus { val status = "POSTPONED" }
case object Abandoned extends MatchStatus { val status = "ABANDONED" }
// @formatter:on

case class Competition(
    id: String,
    name: String,
    round: Option[String] = None
)

case class TeamState(
    name: String,
    score: Int,
    logoAssetName: Option[String] = None,
    teamUrl: Option[String] = None,
    penaltyScore: Option[Int] = None,
    redCards: Option[Int] = None
)

case class FootballMatchContentState(
    matchStatus: MatchStatus,
    kickOffTimestamp: Long,
    homeTeam: TeamState,
    awayTeam: TeamState,
    competition: Competition,
    commentary: Option[String] = None,
    lineupsAvailable: Option[Boolean] = None,
    currentMinute: Option[Int] = None,
    currentPeriodStartTime: Option[Long] = None,
    articleUrl: Option[String] = None
) extends ContentState

object FootballContentJsonFormats {
  // MatchStatus format must be defined first since FootballMatchContentState depends on it
  implicit val matchStatusFormat: Format[MatchStatus] = Format(
    Reads {
      case JsString(s) =>
        s match {
          case "SCHEDULED"              => JsSuccess(Scheduled)
          case "PRE_MATCH"              => JsSuccess(PreMatch)
          case "FIRST_HALF"             => JsSuccess(FirstHalf)
          case "HALF_TIME"              => JsSuccess(HalfTime)
          case "SECOND_HALF"            => JsSuccess(SecondHalf)
          case "EXTRA_TIME_FIRST_HALF"  => JsSuccess(ExtraTimeFirstHalf)
          case "EXTRA_TIME_HALF_TIME"   => JsSuccess(ExtraTimeHalfTime)
          case "EXTRA_TIME_SECOND_HALF" => JsSuccess(ExtraTimeSecondHalf)
          case "PENALTIES"              => JsSuccess(Penalties)
          case "FULL_TIME"              => JsSuccess(FullTime)
          case "POSTPONED"              => JsSuccess(Postponed)
          case "ABANDONED"              => JsSuccess(Abandoned)
          case other                    => JsError(s"Unknown match status: $other")
        }
      case _ => JsError("Expected a JSON string for MatchStatus")
    },
    Writes(ms => JsString(ms.status))
  )

  implicit val competitionFormat: OFormat[Competition] =
    Json.format[Competition]
  implicit val teamStateFormat: OFormat[TeamState] = Json.format[TeamState]
  implicit val footballMatchContentStateFormat: OFormat[FootballMatchContentState] =
    Json.format[FootballMatchContentState]
}

// BROADCAST PAYLOADS //////////////////////////////////////////////////

// Start Live Activity (via broadcast or push)
sealed trait ActivityAttributesType { val `type`: String }
case object FootballMatchAttributesType extends ActivityAttributesType {
  val `type` = "FootballMatchAttributes"
}

sealed trait ActivityAttributes
case class FootballMatchAttributes(matchId: String) extends ActivityAttributes

object ActivityAttributesJsonFormats {
  implicit val activityAttributesTypeFormat: Format[ActivityAttributesType] =
    Format(
      Reads {
        case JsString(s) =>
          s match {
            case "FootballMatchAttributes" =>
              JsSuccess(FootballMatchAttributesType)
            case other => JsError(s"Unknown match status: $other")
          }
        case _ => JsError("Expected a JSON string for ActivityAttributes")
      },
      Writes(ms => JsString(ms.`type`))
    )

  implicit val footballMatchAttributesFormat: OFormat[FootballMatchAttributes] =
    Json.format[FootballMatchAttributes]

  implicit val activityAttributesFormat: OFormat[ActivityAttributes] =
    new OFormat[ActivityAttributes] {
      def writes(a: ActivityAttributes): JsObject = a match {
        case f: FootballMatchAttributes =>
          footballMatchAttributesFormat
            .writes(f)
            .as[JsObject] + ("type" -> JsString("football"))
        // Add cases for other ActivityAttributes subtypes here
      }
      def reads(json: JsValue): JsResult[ActivityAttributes] = {
        (json \ "type").validate[String].flatMap {
          case "football" => footballMatchAttributesFormat.reads(json)
          // Add cases for other ActivityAttributes subtypes here
          case other => JsError(s"Unknown ActivityAttributes type: $other")
        }
      }
    }
}

// Update Live Activity (broadcast)
sealed trait BroadcastApsEvent {
  def timestamp: Long
  def event: String
  def `content-state`: ContentState
}
sealed trait BroadcastBody {
  def aps: BroadcastApsEvent
}

// Start Live Activity (via broadcast)
case class BroadcastStartAps(
    timestamp: Long,
    event: String = "start",
    `content-state`: ContentState,
    `attributes-type`: ActivityAttributesType,
    `attributes`: ActivityAttributes
) extends BroadcastApsEvent

case class BroadcastStartBody(
    aps: BroadcastStartAps
) extends BroadcastBody

// Update Live Activity (broadcast)
case class BroadcastUpdateAps(
    timestamp: Long,
    event: String = "update",
    `content-state`: ContentState,
    `stale-date`: Long
) extends BroadcastApsEvent

case class BroadcastUpdateBody(
    aps: BroadcastUpdateAps
) extends BroadcastBody

// End Live Activity (broadcast)
case class BroadcastEndAps(
    timestamp: Long,
    event: String = "end",
    `content-state`: ContentState,
    `dismissal-date`: Long
) extends BroadcastApsEvent

case class BroadcastEndBody(
    aps: BroadcastEndAps
) extends BroadcastBody

object BroadcastJsonFormats {
  import ActivityAttributesJsonFormats._
  import FootballContentJsonFormats._
  import ContentState.format

  implicit val broadcastStartApsFormat: OFormat[BroadcastStartAps] =
    Json.format[BroadcastStartAps]
  implicit val broadcastStartBodyFormat: OFormat[BroadcastStartBody] =
    Json.format[BroadcastStartBody]
  implicit val broadcastUpdateApsFormat: OFormat[BroadcastUpdateAps] =
    Json.format[BroadcastUpdateAps]
  implicit val broadcastUpdateBodyFormat: OFormat[BroadcastUpdateBody] =
    Json.format[BroadcastUpdateBody]
  implicit val broadcastEndApsFormat: OFormat[BroadcastEndAps] =
    Json.format[BroadcastEndAps]
  implicit val broadcastEndBodyFormat: OFormat[BroadcastEndBody] =
    Json.format[BroadcastEndBody]

  implicit val broadcastApsEventFormat: OFormat[BroadcastApsEvent] =
    new OFormat[BroadcastApsEvent] {
      def writes(e: BroadcastApsEvent): JsObject = e match {
        case s: BroadcastStartAps =>
          broadcastStartApsFormat.writes(s) + ("eventType" -> JsString("start"))
        case u: BroadcastUpdateAps =>
          broadcastUpdateApsFormat.writes(u) + ("eventType" -> JsString(
            "update"
          ))
        case e: BroadcastEndAps =>
          broadcastEndApsFormat.writes(e) + ("eventType" -> JsString("end"))
      }
      def reads(json: JsValue): JsResult[BroadcastApsEvent] =
        (json \ "eventType").validate[String].flatMap {
          case "start"  => broadcastStartApsFormat.reads(json)
          case "update" => broadcastUpdateApsFormat.reads(json)
          case "end"    => broadcastEndApsFormat.reads(json)
          case other    => JsError(s"Unknown BroadcastApsEvent type: $other")
        }
    }

  implicit val broadcastBodyFormat: OFormat[BroadcastBody] =
    new OFormat[BroadcastBody] {
      def writes(b: BroadcastBody): JsObject = b match {
        case s: BroadcastStartBody =>
          broadcastStartBodyFormat.writes(s) + ("bodyType" -> JsString("start"))
        case u: BroadcastUpdateBody =>
          broadcastUpdateBodyFormat.writes(u) + ("bodyType" -> JsString(
            "update"
          ))
        case e: BroadcastEndBody =>
          broadcastEndBodyFormat.writes(e) + ("bodyType" -> JsString("end"))
      }
      def reads(json: JsValue): JsResult[BroadcastBody] =
        (json \ "bodyType").validate[String].flatMap {
          case "start"  => broadcastStartBodyFormat.reads(json)
          case "update" => broadcastUpdateBodyFormat.reads(json)
          case "end"    => broadcastEndBodyFormat.reads(json)
          case other    => JsError(s"Unknown BroadcastBody type: $other")
        }
    }
}
