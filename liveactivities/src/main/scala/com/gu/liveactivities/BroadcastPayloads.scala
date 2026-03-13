package com.gu.liveactivities

import play.api.libs.json._

// GENERIC CONTENT STATE //////////////////////////////////////////////////

sealed trait ContentState
object ContentState {
  implicit val format: OFormat[ContentState] = new OFormat[ContentState] {
    def writes(cs: ContentState): JsObject = cs match {
      case f: FootballMatchContentState =>
        FootballMatchContentState.jf.writes(f).as[JsObject] + ("type" -> JsString("football"))
      // Add cases for other ContentState subtypes here
    }

    def reads(json: JsValue): JsResult[ContentState] = {
      (json \ "type").validate[String].flatMap {
        case "football" => FootballMatchContentState.jf.reads(json)
        // Add cases for other ContentState subtypes here
        case other      => JsError(s"Unknown ContentState type: $other")
      }
    }
  }
}

// FOOTBALL CONTENT STATE //////////////////////////////////////////////////

sealed trait MatchStatus { val status: String }
case object Scheduled           extends MatchStatus { val status = "SCHEDULED" }
case object PreMatch            extends MatchStatus { val status = "PRE_MATCH" }
case object FirstHalf           extends MatchStatus { val status = "FIRST_HALF" }
case object HalfTime            extends MatchStatus { val status = "HALF_TIME" }
case object SecondHalf          extends MatchStatus { val status = "SECOND_HALF" }
case object ExtraTimeFirstHalf  extends MatchStatus { val status = "EXTRA_TIME_FIRST_HALF" }
case object ExtraTimeHalfTime   extends MatchStatus { val status = "EXTRA_TIME_HALF_TIME" }
case object ExtraTimeSecondHalf extends MatchStatus { val status = "EXTRA_TIME_SECOND_HALF" }
case object Penalties           extends MatchStatus { val status = "PENALTIES" }
case object FullTime            extends MatchStatus { val status = "FULL_TIME" }
case object Postponed           extends MatchStatus { val status = "POSTPONED" }
case object Abandoned           extends MatchStatus { val status = "ABANDONED" }
object MatchStatus {
  implicit val format: Format[MatchStatus] = Format(
    Reads {
      case JsString(s) => s match {
        case "SCHEDULED"               => JsSuccess(Scheduled)
        case "PRE_MATCH"               => JsSuccess(PreMatch)
        case "FIRST_HALF"              => JsSuccess(FirstHalf)
        case "HALF_TIME"               => JsSuccess(HalfTime)
        case "SECOND_HALF"             => JsSuccess(SecondHalf)
        case "EXTRA_TIME_FIRST_HALF"   => JsSuccess(ExtraTimeFirstHalf)
        case "EXTRA_TIME_HALF_TIME"    => JsSuccess(ExtraTimeHalfTime)
        case "EXTRA_TIME_SECOND_HALF"  => JsSuccess(ExtraTimeSecondHalf)
        case "PENALTIES"               => JsSuccess(Penalties)
        case "FULL_TIME"               => JsSuccess(FullTime)
        case "POSTPONED"               => JsSuccess(Postponed)
        case "ABANDONED"               => JsSuccess(Abandoned)
        case other                     => JsError(s"Unknown match status: $other")
      }
      case _ => JsError("Expected a JSON string for MatchStatus")
    },
    Writes(ms => JsString(ms.status))
  )
}

case class Competition(
                        id: String, // PA has a competition ID but separate season id.
                        name: String, // Ful competition name with season
                        // stage: Option[String] = None, // TBC
                        round: Option[String] = None
                      )
object Competition {
  implicit val jf: OFormat[Competition] = Json.format[Competition]
}

case class TeamState(
                      name: String,
                      score: Int,
                      logoAssetName: Option[String] = None, // Bundled asset catalog image name for the team logo
                      teamUrl: Option[String] = None, // Deep link URL
                      penaltyScore: Option[Int] = None,
                      redCards: Option[Int] = None
                    )
object TeamState {
  implicit val jf: OFormat[TeamState] = Json.format[TeamState]
}

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
                         articleUrl: Option[String] = None // not optional?? We must have an article?
                       ) extends ContentState

object FootballMatchContentState {
  implicit val jf: OFormat[FootballMatchContentState] = Json.format[FootballMatchContentState]
}


// BROADCAST PAYLOADS //////////////////////////////////////////////////

// Start Live Activity (via broadcast)
sealed trait ActivityAttributesType { val `type`: String }
case object FootballMatchAttributesType extends ActivityAttributesType { val `type` = "FootballMatchAttributes" }
object ActivityAttributesType {
  implicit val format: Format[ActivityAttributesType] = Format(
    Reads {
      case JsString(s) => s match {
        case "FootballMatchAttributes"  => JsSuccess(FootballMatchAttributesType)
        case other                      => JsError(s"Unknown match status: $other")
      }
      case _ => JsError("Expected a JSON string for ActivityAttributes")
    },
    Writes(ms => JsString(ms.`type`))
  )
}

sealed trait ActivityAttributes
object ActivityAttributes {
  implicit val format: OFormat[ActivityAttributes] = new OFormat[ActivityAttributes] {
    def writes(a: ActivityAttributes): JsObject = a match {
      case f: FootballMatchAttributes =>
        FootballMatchAttributes.jf.writes(f).as[JsObject] + ("type" -> JsString("football"))
      // Add cases for other ActivityAttributes subtypes here
    }
    def reads(json: JsValue): JsResult[ActivityAttributes] = {
      (json \ "type").validate[String].flatMap {
        case "football" => FootballMatchAttributes.jf.reads(json)
        // Add cases for other ActivityAttributes subtypes here
        case other      => JsError(s"Unknown ActivityAttributes type: $other")
      }
    }
  }
}
case class FootballMatchAttributes(matchId: String) extends ActivityAttributes
object FootballMatchAttributes {
  implicit val jf: OFormat[FootballMatchAttributes] = Json.format[FootballMatchAttributes]
}

// Update Live Activity (broadcast)
case class BroadcastStartAps(
                               timestamp: Long,
                               event: String = "start",
                               `content-state`: ContentState,
                               `attributes-type`: ActivityAttributesType,
                               `attributes`: ActivityAttributes
                             )
object BroadcastStartAps {
  implicit val jf: OFormat[BroadcastStartAps] = Json.format[BroadcastStartAps]
}


case class BroadcastStartBody(
                                aps: BroadcastStartAps
                              )
object BroadcastStartBody {
  implicit val jf: OFormat[BroadcastStartBody] = Json.format[BroadcastStartBody]
}


// Update Live Activity (broadcast)
case class BroadcastUpdateAps(
                      timestamp: Long,
                      event: String = "update",
                      `content-state`: ContentState,
                      `stale-date`: Long
                      // alert: Optional noticiation alert to show when the update is received
                    )
object BroadcastUpdateAps {
  implicit val jf: OFormat[BroadcastUpdateAps] = Json.format[BroadcastUpdateAps]
}


case class BroadcastUpdateBody(
                       aps: BroadcastUpdateAps
                     )
object BroadcastUpdateBody {
  implicit val jf: OFormat[BroadcastUpdateBody] = Json.format[BroadcastUpdateBody]
}


// End Live Activity (broadcast)
case class BroadcastEndAps(
                   timestamp: Long,
                   event: String = "end",
                   `content-state`: ContentState,
                   // Optional unix timestamp to set when the Live Activity is dismissed from Lock Screen (default: up to 4hrs after end). Use a past date to dismiss immediately.
                   `dismissal-date`: Long
                 )
object BroadcastEndAps {
  implicit val jf: OFormat[BroadcastEndAps] = Json.format[BroadcastEndAps]
}


case class BroadcastEndBody(
                    aps: BroadcastEndAps
                  )
object BroadcastEndBody {
  implicit val jf: OFormat[BroadcastEndBody] = Json.format[BroadcastEndBody]
}



