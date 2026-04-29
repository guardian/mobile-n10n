package com.gu.mobile.notifications.client.models.liveActitivites

import play.api.libs.json._

/**
 * These are the Live Activity Content models used for sending live activity
 * updates to Apple APNS service.
 **/

// GENERIC CONTENT STATE //////////////////////////////////////////////////
sealed trait ContentState
object ContentState {
  import FootballContentJsonFormats._

  implicit val contentStateFormat: OFormat[ContentState] = new OFormat[ContentState] {
    def writes(cs: ContentState): JsObject = cs match {
      case f: FootballMatchContentState => footballMatchContentStateFormat.writes(f)
    }
    def reads(json: JsValue): JsResult[ContentState] = {
      // todo - if we add more content states for other live activity types
      // todo - check adding a _type field to the json?
      // Try FootballMatchContentState - could check a distinguishing field if needed
      footballMatchContentStateFormat.reads(json)
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

object MatchStatus {
  def fromString(s: String): MatchStatus = s match {
    case "Pre-match" | "prematch"                        => PreMatch
    case "MatchInProgress" | "1st Half" | "first_half"   => FirstHalf
    case "HalfTime" | "Half Time" | "half_time"          => HalfTime
    case "2nd Half" | "second_half"                      => SecondHalf
    case "Extra Time" | "ET 1st Half"                    => ExtraTimeFirstHalf
    case "ET Half Time"                                  => ExtraTimeHalfTime
    case "ET 2nd Half"                                   => ExtraTimeSecondHalf
    case "Penalties"                                     => Penalties
    case "MatchComplete" | "MC" | "Full Time" | "Result" => FullTime
    case "Postponed"                                     => Postponed
    case "Abandoned"                                     => Abandoned
    case _                                               => Scheduled
  }
}

case class Competition(
    name: String,
    round: Option[String] = None
)

case class TeamState(
    name: String,
    score: Int = 0,
    logoAssetName: Option[String] = None,
    teamUrl: Option[String] = None,
    penaltyScore: Option[Int] = None,
    redCards: Int = 0
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
  implicit val footballMatchContentStateFormat: OFormat[FootballMatchContentState] = Json.format[FootballMatchContentState]
}
