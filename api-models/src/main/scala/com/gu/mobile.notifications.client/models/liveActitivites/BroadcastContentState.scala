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
  def fromString(s: String): MatchStatus = statuses.getOrElse(s, s) match {
    case "Prematch"                                      => PreMatch
    case "1st"                                           => FirstHalf
    case "HT"                                            => HalfTime
    case "2nd"                                           => SecondHalf
    case "ET" | "ETS"                                    => ExtraTimeFirstHalf
    case "ETHT"                                          => ExtraTimeHalfTime
    case "ETSHS"                                         => ExtraTimeSecondHalf
    case "PT"                                            => Penalties
    case "FT"                                            => FullTime
    case "Postponed"                                     => Postponed
    case "Abandoned"                                     => Abandoned
    case _                                               => Scheduled
  }

  // How PA match status are handled differs from push notification
  private val statuses = Map(
    ("KO", "1st"), // The Match has started (Kicked Off).

    ("HT", "HT"), // The Referee has blown the whistle for Half Time.

    ("SHS", "2nd"), // The Second Half of the Match has Started.

    ("FT", "FT"), // The Referee has blown the whistle for Full Time.
    ("PTFT", "FT"), // Penalty ShooT Full Time.
    ("Result", "FT"), // The Result is official.
    ("ETFT", "FT"), // Extra Time, Full Time has been blown.
    ("MC", "FT"), // Match has been Completed.

    ("FTET", "ET"), // Full Time, Extra Time it to be played.
    ("ETS", "ETS"), // Extra Time has Started.
    ("ETHT", "ETHT"), // Extra Time Half Time has been called.
    ("ETSHS", "ETSHS"), // Extra Time, Second Half has Started.

    ("FTPT", "PT"), // Full Time, Penalties are To be played.
    ("PT", "PT"), // Penalty ShooT Out has started.
    ("ETFTPT", "PT"), // Extra Time, Full Time, Penalties are To be played.

    // Prematch
    ("Fixture", "Prematch"), // fixture maps to 1st just in case the matchInfo and event feed are out of sync
    ("-", "Prematch"), // same as above
    ("New", "Prematch"), // New Match has been added to our data.

    // edge cases
    // TODO these need to be addressed. A match can be suspended and resumed within x days?)
    ("Suspended", "S"), // Match has been Suspended. // todo when does this happen?
    ("Resumed", "R"), // Match has been Resumed. // todo what game time does this map to? first half second half? match minute?
    ("Abandoned", "A"), // Match has been Abandoned.
    ("Cancelled", "C") // A Match has been Cancelled.
    // POSTPONED???
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

  implicit val competitionFormat: OFormat[Competition] = Json.format[Competition]
  implicit val penaltyShootoutStateFormat: OFormat[PenaltyShootoutState] = Json.format[PenaltyShootoutState]
  implicit val teamStateFormat: OFormat[TeamState] = Json.format[TeamState]
  implicit val footballMatchContentStateFormat: OFormat[FootballMatchContentState] = Json.format[FootballMatchContentState]
}
