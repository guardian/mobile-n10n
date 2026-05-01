package com.gu.mobile.notifications.football.models

import ch.qos.logback.core.model.conditional.ElseModel
import com.gu.mobile.notifications.client.models.liveActitivites.PenaltyShootoutState
import com.gu.mobile.notifications.client.models.{DefaultGoalType, GoalType, MissedShootoutResult, OwnGoalType, PenaltyGoalType, SavedShootoutResult, ScoredShootoutResult, ShootoutResultType}

import scala.PartialFunction._
import scala.util.Try

sealed trait FootballMatchEvent {
  def eventId: String
}

object FootballMatchEvent {
  def fromPaMatchEvent(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam)(event: pa.MatchEvent): Option[FootballMatchEvent] =
    MatchPhaseEvent.fromEvent(event) orElse
      Goal.fromEvent(homeTeam, awayTeam)(event) orElse
      Dismissal.fromEvent(homeTeam,awayTeam)(event) orElse
      PenaltyShootoutKick.fromEvent(homeTeam, awayTeam)(event)
}

object Score {
  def fromGoals(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam, goals: List[Goal]): Score = {
    val home = goals.count(_.scoringTeam == homeTeam)
    val away = goals.count(_.scoringTeam == awayTeam)

    Score(home, away)
  }
}

case class Score(home: Int, away: Int)

object RedCards {
  def fromDismissals(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam, dismissals: List[Dismissal]): RedCards = {
    val home = dismissals.count(_.team == homeTeam)
    val away = dismissals.count(_.team == awayTeam)

    RedCards(home, away)
  }
}
case class RedCards(home: Int, away: Int)

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

case class GoalContext(
    home: pa.MatchDayTeam,
    away: pa.MatchDayTeam,
    matchId: String,
    score: Score
)

case class PenaltyShootoutKick(
  result: ShootoutResultType,
  playerName: String,
  kickingTeam: pa.MatchDayTeam,
  otherTeam: pa.MatchDayTeam,
  minute: Int,
  eventId: String
) extends FootballMatchEvent

object PenaltyShootoutKick {
  def fromEvent(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam)(event: pa.MatchEvent): Option[PenaltyShootoutKick] = for {
    result <- shootoutPenaltyResultFromString(event.eventType)
    player <- event.players.headOption
    kickingTeam = if (player.teamID == homeTeam.id) homeTeam else awayTeam
    otherTeam = if (player.teamID == homeTeam.id) awayTeam else homeTeam
    eventTime <- event.eventTime
    eventMinute <- Try(eventTime.toInt).toOption
    eventId <- event.id
  } yield PenaltyShootoutKick(
    result,
    player.name,
    kickingTeam,
    otherTeam,
    eventMinute,
    eventId
  )

  private def shootoutPenaltyResultFromString(s: String): Option[ShootoutResultType] = condOpt(s) {
    case "shootoutGoal" => ScoredShootoutResult
    case "shootoutMiss" => MissedShootoutResult
    case "shootoutSave" => SavedShootoutResult
  }
}

object PenaltyShootoutScore {
  def fromPenaltyShootoutKicks(homeTeam: pa.MatchDayTeam, awayTeam: pa.MatchDayTeam, shootoutResults: List[PenaltyShootoutKick]): Option[PenaltyShootoutScore] = {
    if (shootoutResults.isEmpty) None
    else {
      val homeScored = shootoutResults.count(r => r.kickingTeam.id == homeTeam.id && r.result == ScoredShootoutResult)
      val homeMissed = shootoutResults.count(r => r.kickingTeam.id == homeTeam.id && r.result == MissedShootoutResult)
      val homeSaved = shootoutResults.count(r => r.kickingTeam.id == homeTeam.id && r.result == SavedShootoutResult)
      val awayScored = shootoutResults.count(r => r.kickingTeam.id == awayTeam.id && r.result == ScoredShootoutResult)
      val awayMissed = shootoutResults.count(r => r.kickingTeam.id == awayTeam.id && r.result == MissedShootoutResult)
      val awaySaved = shootoutResults.count(r => r.kickingTeam.id == awayTeam.id && r.result == SavedShootoutResult)
      Some(PenaltyShootoutScore(homeScored, homeMissed, homeSaved, awayScored, awayMissed, awaySaved))
    }
  }

   def toPenaltyShootoutState(score: Option[PenaltyShootoutScore], isHomeTeam: Boolean): Option[PenaltyShootoutState] =
     if (isHomeTeam) {
       score.map(s => PenaltyShootoutState(s.homeScored, s.homeMissed, s.homeSaved))
     } else {
       score.map(s => PenaltyShootoutState(s.awayScored, s.awayMissed, s.awaySaved))
     }

}
case class PenaltyShootoutScore(homeScored: Int = 0, homeMissed: Int = 0, homeSaved: Int = 0, awayScored: Int = 0, awayMissed: Int = 0, awaySaved: Int = 0)


trait MatchPhaseEvent extends FootballMatchEvent

object MatchPhaseEvent {
  def fromEvent(event: pa.MatchEvent): Option[MatchPhaseEvent] = {
    val eventId = event.id.getOrElse("")
    condOpt(event.eventType) {
      case "timeline" if event.matchTime.contains("0:00") => KickOff(eventId)
      case "full-time"                                    => FullTime(eventId) // todo use this to end activity?
      case "half-time"                                    => HalfTime(eventId)
      case "second-half"                                  => SecondHalf(eventId)
      case "create-channel"                               => CreateChannel(eventId)
      case "start-live-activity"                          => StartLiveActivity(eventId)
      case "end-live-activity"                            => EndLiveActivity(eventId)
    }
  }
}

case class KickOff(eventId: String) extends MatchPhaseEvent
case class FullTime(eventId: String) extends MatchPhaseEvent
case class HalfTime(eventId: String) extends MatchPhaseEvent
case class SecondHalf(eventId: String) extends MatchPhaseEvent
case class CreateChannel(eventId: String) extends MatchPhaseEvent
case class StartLiveActivity(eventId: String) extends MatchPhaseEvent
case class EndLiveActivity(eventId: String) extends MatchPhaseEvent
