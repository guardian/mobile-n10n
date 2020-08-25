package com.gu.mobile.notifications.football.models

import com.gu.mobile.notifications.client.models.{DefaultGoalType, GoalType, OwnGoalType, PenaltyGoalType}

import scala.PartialFunction._
import scala.util.Try

sealed trait FootballMatchEvent {
  def eventId: String
}

object FootballMatchEvent {
  def fromPaMatchEvent(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam)(event: pa.MatchEvent): Option[FootballMatchEvent] =
    MatchPhaseEvent.fromEvent(event) orElse
      Goal.fromEvent(homeTeam, awayTeam)(event) orElse
      Dismissal.fromEvent(homeTeam,awayTeam)(event)
}

object Score {
  def fromGoals(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam, goals: List[Goal]): Score = {
    val home = goals.count(_.scoringTeam == homeTeam)
    val away = goals.count(_.scoringTeam == awayTeam)

    Score(home, away)
  }
}

case class Score(home: Int, away: Int)

case class Dismissal(
  eventId: String,
  playerName: String,
  team: pa.MatchDayTeam,
  minute: Int,
  addedTime: Option[String]
) extends FootballMatchEvent {
}
object Dismissal {
  def fromEvent(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam)(event: pa.MatchEvent): Option[Dismissal] = {
    condOpt(event.eventType) {
      case "dismissal" => for {
          eventId <- event.id
          player <- event.players.headOption
          team <- Seq(homeTeam, awayTeam).find(_.id == player.teamID)
          eventTime <- event.eventTime
          eventMinute  <- Try(eventTime.toInt).toOption
        } yield Dismissal(
          eventId,
          player.name,
          team,
          eventMinute,
          event.addedTime.filterNot(_ == "0:00")
      )
      }
  }.flatten
}

case class Goal(
  goalType: GoalType,
  scorerName: String,
  scoringTeam: pa.MatchDayTeam,
  otherTeam: pa.MatchDayTeam,
  minute: Int,
  addedTime: Option[String],
  eventId: String
) extends FootballMatchEvent

object Goal {

  def fromEvent(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam)(event: pa.MatchEvent): Option[Goal] = for {
    goalType <- goalTypeFromString(event.eventType)
    scorer <- event.players.headOption
    eventTime <- event.eventTime
    eventMinute <- Try(eventTime.toInt).toOption
    awayTeamScorer = scorer.teamID == awayTeam.id
    ownGoal = goalType == OwnGoalType
    teams = (homeTeam, awayTeam)
    (scoringTeam, otherTeam) = if (awayTeamScorer ^ ownGoal) teams.swap else teams
  } yield Goal(
      goalType,
      scorer.name,
      scoringTeam,
      otherTeam,
      eventMinute,
      event.addedTime.filterNot(_ == "0:00"),
      event.id.getOrElse("")
  )

  private def goalTypeFromString(s: String): Option[GoalType] = condOpt(s) {
    case "goal" => DefaultGoalType
    case "own goal" => OwnGoalType
    case "goal from penalty" => PenaltyGoalType
  }
}


trait MatchPhaseEvent extends FootballMatchEvent

object MatchPhaseEvent {
  def fromEvent(event: pa.MatchEvent): Option[MatchPhaseEvent] = {
    val eventId = event.id.getOrElse("")
    condOpt(event.eventType) {
      case "timeline" if event.matchTime.contains("0:00") => KickOff(eventId)
      case "full-time" => FullTime(eventId)
      case "half-time" => HalfTime(eventId)
      case "second-half" => SecondHalf(eventId)
    }
  }
}

case class KickOff(eventId: String) extends MatchPhaseEvent
case class FullTime(eventId: String) extends MatchPhaseEvent
case class HalfTime(eventId: String) extends MatchPhaseEvent
case class SecondHalf(eventId: String) extends MatchPhaseEvent

case class GoalContext(
  home: pa.MatchDayTeam,
  away: pa.MatchDayTeam,
  matchId: String,
  score: Score
)