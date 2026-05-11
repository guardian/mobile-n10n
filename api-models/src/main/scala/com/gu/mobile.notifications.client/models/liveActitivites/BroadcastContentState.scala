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
case object ExtraTimeToBePlayed extends MatchStatus { val status = "EXTRA_TIME_TO_BE_PLAYED" }
case object ExtraTimeFirstHalf extends MatchStatus { val status = "EXTRA_TIME_FIRST_HALF" }
case object ExtraTimeHalfTime extends MatchStatus { val status = "EXTRA_TIME_HALF_TIME" }
case object ExtraTimeSecondHalf extends MatchStatus { val status = "EXTRA_TIME_SECOND_HALF" }
case object PenaltiesToBePlayed extends MatchStatus { val status = "PENALTIES_TO_BE_PLAYED" }
case object Penalties extends MatchStatus { val status = "PENALTIES" }
case object FullTime extends MatchStatus { val status = "FULL_TIME" }
case object Postponed extends MatchStatus { val status = "POSTPONED" }
case object Suspended extends MatchStatus { val status = "SUSPENDED" }
case object Abandoned extends MatchStatus { val status = "ABANDONED" }
case object Resumed extends MatchStatus { val status = "RESUMED" }
case object Cancelled extends MatchStatus { val status = "CANCELLED" }
// @formatter:on

object MatchStatus {
  def fromString(s: String): MatchStatus = statuses.getOrElse(s, Scheduled)

  // How PA match status are handled here differs from push notification mappings
  private val statuses: Map[String, MatchStatus] = Map(
    ("Fixture", PreMatch), //
    ("-", PreMatch), // seen in the wild
    ("New", PreMatch), //

    ("KO", FirstHalf), // The Match has started (Kicked Off).

    ("HT", HalfTime), // The Referee has blown the whistle for Half Time.

    ("SHS", SecondHalf), // The Second Half of the Match has Started.

    ("FT", FullTime), // The Referee has blown the whistle for Full Time.
    ("Result", FullTime), // The Result is official.
    ("MC", FullTime), // Match has been Completed.

    ("FTET", ExtraTimeToBePlayed), // Full Time, Extra Time it to be played.
    ("ETS", ExtraTimeFirstHalf), // Extra Time has Started.
    ("ETHT", ExtraTimeHalfTime), // Extra Time Half Time has been called.
    ("ETSHS", ExtraTimeSecondHalf), // Extra Time, Second Half has Started.
    ("ETFT", ExtraTimeSecondHalf), // Extra Time, Full Time has been blown.

    ("FTPT", PenaltiesToBePlayed), // Full Time, Penalties are To be played.
    ("ETFTPT", PenaltiesToBePlayed), // Extra Time, Full Time, Penalties are To be played.
    ("PT", Penalties), // Penalty ShooT Out has started.
    ("PTFT", Penalties), // Penalty ShooT Full Time.

    // edge cases
    ("Suspended", Suspended), // Match has been Suspended.
    ("Resumed", Resumed), // Match has been Resumed. // todo can match phase be determined by match minute?
    ("Abandoned", Abandoned), // Match has been Abandoned.
    ("Postponed", Postponed), // A Match has been Postponed.
    ("Cancelled", Cancelled) // A Match has been Cancelled.
  )
}

case class Competition(
    id: String,
    name: String,
    round: Option[String] = None // World Cup Group name
)

case class PenaltyShootoutState(
    scored: Int = 0,
    missed: Int = 0,
    saved: Int = 0,
)

case class TeamState(
    name: String,
    logoAssetName: Option[String] = None,
    teamUrl: Option[String] = None,
    score: Int = 0,
    redCards: Int = 0,
    penaltyScore: Option[PenaltyShootoutState] = None
)

case class FootballMatchContentState(
    matchStatus: MatchStatus,
    kickOffTimestamp: Long,
    homeTeam: TeamState,
    awayTeam: TeamState,
    competition: Competition,
    commentary: Option[String] = None,
    lineupsAvailable: Boolean = false,
    currentMinute: Option[Int] = None,
    currentPeriodStartTime: Option[Long] = None,
    articleUrl: Option[String] = None,
    matchInfoUrl: String
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
          case "EXTRA_TIME_TO_BE_PLAYED" => JsSuccess(ExtraTimeToBePlayed)
          case "EXTRA_TIME_FIRST_HALF"  => JsSuccess(ExtraTimeFirstHalf)
          case "EXTRA_TIME_HALF_TIME"   => JsSuccess(ExtraTimeHalfTime)
          case "EXTRA_TIME_SECOND_HALF" => JsSuccess(ExtraTimeSecondHalf)
          case "PENALTIES_TO_BE_PLAYED" => JsSuccess(PenaltiesToBePlayed)
          case "PENALTIES"              => JsSuccess(Penalties)
          case "FULL_TIME"              => JsSuccess(FullTime)
          case "SUSPENDED"              => JsSuccess(Suspended)
          case "RESUMED"                => JsSuccess(Resumed)
          case "POSTPONED"              => JsSuccess(Postponed)
          case "ABANDONED"              => JsSuccess(Abandoned)
          case "CANCELLED"              => JsSuccess(Cancelled)
          case other                    => JsError(s"Unknown match status: $other")
        }
      case _ => JsError("Expected a JSON string for MatchStatus")
    },
    Writes(ms => JsString(ms.status))
  )

  implicit val competitionFormat: OFormat[Competition] = Json.format[Competition]
  implicit val penaltyShootoutStateFormat: OFormat[PenaltyShootoutState] = Json.format[PenaltyShootoutState]
  implicit val teamStateFormat: OFormat[TeamState] = Json.format[TeamState]
  implicit val footballMatchContentStateFormat: OFormat[FootballMatchContentState] = Json.format[FootballMatchContentState]
}
