package com.gu.mobile.notifications.football.notificationbuilders

import java.net.URI
import java.util.UUID

import com.gu.mobile.notifications.client.models.Importance.{Importance, Major, Minor}
import com.gu.mobile.notifications.client.models._
import com.gu.mobile.notifications.football.models._
import pa.{MatchDay, MatchDayTeam}

import scala.PartialFunction.condOpt

class MatchStatusNotificationBuilder(mapiHost: String) {

  def build(
    triggeringEvent: FootballMatchEvent,
    matchInfo: MatchDay,
    previousEvents: List[FootballMatchEvent],
    articleId: Option[String]
  ): FootballMatchStatusPayload = {
    val topics = List(
      Topic(TopicTypes.FootballTeam, matchInfo.homeTeam.id),
      Topic(TopicTypes.FootballTeam, matchInfo.awayTeam.id),
      Topic(TopicTypes.FootballMatch, matchInfo.id)
    )

    val allEvents = triggeringEvent :: previousEvents
    val goals = allEvents.collect { case g: Goal => g }
    val score = Score.fromGoals(matchInfo.homeTeam, matchInfo.awayTeam, goals)

    val status = statuses.getOrElse(matchInfo.matchStatus, matchInfo.matchStatus)

    FootballMatchStatusPayload(
      title = eventTitle(triggeringEvent),
      message = mainMessage(triggeringEvent, transformTeamName(matchInfo.homeTeam.name), transformTeamName(matchInfo.awayTeam.name), score, status),
      sender = "mobile-notifications-football-lambda",
      awayTeamName = transformTeamName(matchInfo.awayTeam.name),
      awayTeamScore = score.away,
      awayTeamMessage = teamMessage(matchInfo.awayTeam, allEvents),
      awayTeamId = matchInfo.awayTeam.id,
      homeTeamName = transformTeamName(matchInfo.homeTeam.name),
      homeTeamScore = score.home,
      homeTeamMessage = teamMessage(matchInfo.homeTeam, allEvents),
      homeTeamId = matchInfo.homeTeam.id,
      matchId = matchInfo.id,
      competitionName = matchInfo.competition.map(_.name),
      venue = matchInfo.venue.map(_.name).filter(_.nonEmpty),
      matchInfoUri = new URI(s"$mapiHost/sport/football/matches/${matchInfo.id}"),
      articleUri = articleId.map(id => new URI(s"$mapiHost/items/$id")),
      importance = importance(triggeringEvent),
      topic = topics,
      matchStatus = status,
      eventId = UUID.nameUUIDFromBytes(triggeringEvent.eventId.getBytes).toString,
      debug = false
    )
  }

  def transformTeamName(name: String): String = name.replace(" Ladies", "")

  private def goalDescription(goal: Goal) = {
    val extraInfo = {
      val goalTypeInfo = condOpt(goal.goalType) {
        case OwnGoalType => "o.g."
        case PenaltyGoalType => "pen"
      }

      val addedTimeInfo = goal.addedTime.map("+" + _)

      List(goalTypeInfo, addedTimeInfo).flatten match {
        case Nil => ""
        case xs => s" ${xs.mkString(" ")}"
      }
    }

    s"""${goal.scorerName} ${goal.minute}'$extraInfo""".stripMargin
  }

  def dismissalTeamMsg(dismissal: Dismissal):String = {
    val extraInfo = {
      dismissal.addedTime.map("+" + _).getOrElse("")
    }
    s"Red card: ${dismissal.playerName} ${dismissal.minute}'$extraInfo".stripMargin
  }

  private def teamMessage(team: MatchDayTeam, events: List[FootballMatchEvent]) = {
    val msg = events.collect {
      case g: Goal if g.scoringTeam == team => goalDescription(g)
      case d: Dismissal if d.team == team => dismissalTeamMsg(d)
    }.mkString("\n")
    if (msg == "") " " else msg
  }



  private def mainMessage(triggeringEvent: FootballMatchEvent, homeTeamName: String, awayTeamName: String, score: Score, matchStatus: String) = {

    def goalMsg(goal: Goal) = {
      val extraInfo = {
        val goalTypeInfo = condOpt(goal.goalType) {
          case OwnGoalType => "o.g."
          case PenaltyGoalType => "pen"
        }

        val addedTimeInfo = goal.addedTime.map("+" + _)

        List(goalTypeInfo, addedTimeInfo).flatten match {
          case Nil => ""
          case xs => s" (${xs.mkString(" ")})"
        }
      }


      s"""${homeTeamName} ${score.home}-${score.away} ${awayTeamName} ($matchStatus)
         |${goal.scorerName} ${goal.minute}min$extraInfo""".stripMargin
    }
    def dismissalMsg(dismissal: Dismissal):String = {
      val extraInfo = {
        dismissal.addedTime.map("+" + _).getOrElse("")
      }

      s"""${homeTeamName} ${score.home}-${score.away} ${awayTeamName} ($matchStatus)
         |${dismissal.playerName} (${dismissal.team.name}) ${dismissal.minute}min$extraInfo""".stripMargin
    }



    triggeringEvent match {
      case g: Goal => goalMsg(g)
      case dismissal: Dismissal => dismissalMsg(dismissal)
      case _ => s"""${homeTeamName} ${score.home}-${score.away} ${awayTeamName} ($matchStatus)"""
    }
  }

  private def eventTitle(fme: FootballMatchEvent): String = fme match {
    case _: Goal => "Goal!"
    case HalfTime(_) => "Half-time"
    case KickOff(_) => "Kick-off!"
    case SecondHalf(_) => "Second-half start"
    case FullTime(_) => "Full-Time"
    case _:Dismissal => "Red card"
    case _ => "The Guardian"
  }

  private def importance(fme: FootballMatchEvent): Importance = fme match {
    case _: Goal => Major
    case _ => Minor
  }

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
    ("ETS", "ET"), // Extra Time has Started.
    ("ETHT", "ET"), // Extra Time Half Time has been called.
    ("ETSHS", "ET"), // Extra Time, Second Half has Started.

    ("FTPT", "PT"), // Full Time, Penalties are To be played.
    ("PT", "PT"), // Penalty ShooT Out has started.
    ("ETFTPT", "PT"), // Extra Time, Full Time, Penalties are To be played.

    ("Suspended", "S"), // Match has been Suspended.

    ("Fixture", "1st"), // fixture maps to 1st just in case the matchInfo and event feed are out of sync
    ("-", "1st"), // same as above

    // don't really expect to see these (the way we handle data)
    ("Resumed", "R"), // Match has been Resumed.
    ("Abandoned", "A"), // Match has been Abandoned.
    ("New", "N"), // Match A New Match has been added to our data.
    ("Cancelled", "C") // A Match has been Cancelled.
  )
}
