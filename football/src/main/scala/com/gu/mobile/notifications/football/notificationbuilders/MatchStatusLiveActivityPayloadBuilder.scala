package com.gu.mobile.notifications.football.notificationbuilders

import com.gu.mobile.notifications.client.models.liveActitivites.{Competition, CreateChannelEvent, EndLiveActivityEvent, FootballLiveActivity, FootballMatchContentState, LiveActivityPayload, MatchStatus, StartLiveActivityEvent, TeamState, UpdateLiveActivityEvent}
import com.gu.mobile.notifications.football.models._
import pa.MatchDay

import java.net.URI
import java.util.{Date, UUID}

class MatchStatusLiveActivityPayloadBuilder {

  def build(
      triggeringEvent: FootballMatchEvent,
      matchInfo: MatchDay,
      previousEvents: List[FootballMatchEvent],
      articleId: Option[String]
  ): LiveActivityPayload = {

    val allEvents = triggeringEvent :: previousEvents
    val goals = allEvents.collect { case g: Goal => g }
    val score = Score.fromGoals(matchInfo.homeTeam, matchInfo.awayTeam, goals)
    val dismissals = allEvents.collect { case d: Dismissal => d }
    val redCards = RedCards.fromDismissals(matchInfo.homeTeam, matchInfo.awayTeam, dismissals)
    val penaltyShootoutKicks = allEvents.collect { case psr: PenaltyShootoutKick => psr }
    val penaltyShootoutScore = PenaltyShootoutScore.fromPenaltyShootoutKicks(matchInfo.homeTeam, matchInfo.awayTeam, penaltyShootoutKicks)

    val currentMinute: Option[Int] = triggeringEvent match {
      case d:Dismissal => Some(d.minute)
      case g:Goal => Some(g.minute)
      case p: PenaltyShootoutKick => Some(p.minute)
      case phase: MatchPhaseEvent => phase.currentMinute
      case _ => None
    }

    // TODO this is hard coded mostly for now.
    val contentState = FootballMatchContentState(
      matchStatus = MatchStatus.fromString(matchInfo.matchStatus),
      kickOffTimestamp = matchInfo.date.toEpochSecond, // included date and time
      homeTeam = TeamState(
        id = matchInfo.homeTeam.id,
        name = transformTeamName(matchInfo.homeTeam.name),
        score = score.home,
        logoAssetName = None, // tbc
        teamUrl = None, // tbc
        redCards = redCards.home,
        penaltyScore = PenaltyShootoutScore.toPenaltyShootoutState(penaltyShootoutScore, isHomeTeam = true)
      ),
      awayTeam = TeamState(
        id = matchInfo.awayTeam.id,
        name = transformTeamName(matchInfo.awayTeam.name),
        score = score.away,
        logoAssetName = None, // tbc
        teamUrl = None, // tbc
        redCards = redCards.away,
        penaltyScore = PenaltyShootoutScore.toPenaltyShootoutState(penaltyShootoutScore, isHomeTeam = false)
      ),
      competition = Competition(
        id = matchInfo.competition.map(_.id).getOrElse(""),
        name = matchInfo.competition.map(_.name).getOrElse(""),
        round = matchInfo.round.name.filter(_ != "League") // Round includes world cup grouping, "semi-final" etc.
      ),
      commentary = matchInfo.comments,
      lineupsAvailable = matchInfo.lineupsAvailable,
      currentMinute = currentMinute,
      currentPeriodStartTime = None,
      articleUrl = articleId.map(id => new URI(s"http://www.theguardian.com/$id").toString),
      matchInfoUrl = new URI(s"http://www.theguardian.com/football/match/${matchInfo.id}").toString
    )

    // certain type of triggering match event types will trigger different live activity event type.
    val liveActivityEventType = triggeringEvent match {
      case Abandoned(_) => EndLiveActivityEvent
      case Cancelled(_) => EndLiveActivityEvent
      case CreateChannel(_) => CreateChannelEvent
//      case StartLiveActivity(_) => StartLiveActivityEvent // we do not want to use start yet
      case EndLiveActivity(_) => EndLiveActivityEvent
      case _ => UpdateLiveActivityEvent
    }

    // Deterministic ID used for deduplicating match events (matchId + eventId)
    val derivedId = s"football-match-status/${matchInfo.id}/${triggeringEvent.eventId}"

    LiveActivityPayload(
      id =
        UUID.nameUUIDFromBytes(derivedId.getBytes),
      eventType = liveActivityEventType,
      liveActivityType = FootballLiveActivity,
      liveActivityID =
        matchInfo.id, // Match ID in the case of football, tbc for other sports/events
      dynamoStoreData = None, //  TBC
      broadcastContentStateData = Some(contentState),
      eventTimestamp =
        new Date().getTime, // Now (not the PA event time)
    )

  }

  def transformTeamName(name: String): String = name.replace(" Ladies", "")

}
