package com.gu.mobile.notifications.football.notificationbuilders

import com.gu.mobile.notifications.client.models._
import com.gu.mobile.notifications.client.models.liveActitivites.{Competition, FootballLiveActivity, FootballMatchContentState, LiveActivityPayload, Scheduled, TeamState, UpdateLiveActivityEvent, StartLiveActivityEvent, CreateChannelEvent, EndLiveActivityEvent}
import com.gu.mobile.notifications.football.models._
import pa.MatchDay

import java.util.{Date, UUID}

class MatchStatusLiveActivityPayloadBuilder(mapiHost: String) {

  def build(
      triggeringEvent: FootballMatchEvent,
      matchInfo: MatchDay,
      previousEvents: List[FootballMatchEvent],
      articleId: Option[String]
  ): LiveActivityPayload = {

    val topics = List(
      Topic(TopicTypes.FootballTeam, matchInfo.homeTeam.id),
      Topic(TopicTypes.FootballTeam, matchInfo.awayTeam.id),
      Topic(TopicTypes.FootballMatch, matchInfo.id)
    )

    val allEvents = triggeringEvent :: previousEvents
    val goals = allEvents.collect { case g: Goal => g }
    val score = Score.fromGoals(matchInfo.homeTeam, matchInfo.awayTeam, goals)
    val dismissals = allEvents.collect { case d: Dismissal => d }
    val redCards = RedCards.fromDismissals(matchInfo.homeTeam, matchInfo.awayTeam, dismissals)
    val penaltyShootoutKicks = allEvents.collect { case psr: PenaltyShootoutKick => psr }
    val penaltyShootoutScore = PenaltyShootoutScore.fromPenaltyShootoutKicks(matchInfo.homeTeam, matchInfo.awayTeam, penaltyShootoutKicks)

    // TODO this is hard coded mostly for now.
    val contentState = FootballMatchContentState(
      matchStatus = Scheduled,
      kickOffTimestamp = matchInfo.date.toEpochSecond, // included date and time
      homeTeam = TeamState(
        name = transformTeamName(matchInfo.homeTeam.name),
        score = score.home,
        logoAssetName = None, // tbc
        teamUrl = None, // tbc
        redCards = redCards.home,
        penaltyScore = PenaltyShootoutScore.toPenaltyShootoutState(penaltyShootoutScore, true)
      ),
      awayTeam = TeamState(
        name = transformTeamName(matchInfo.awayTeam.name),
        score = score.away,
        logoAssetName = None, // tbc
        teamUrl = None, // tbc
        redCards = redCards.away,
        penaltyScore = PenaltyShootoutScore.toPenaltyShootoutState(penaltyShootoutScore, false)
      ),
      competition = Competition(
        id = matchInfo.competition.map(_.id).getOrElse(""),
        name = matchInfo.competition.map(_.name).getOrElse(""),
        round = matchInfo.round.name // World Cup Group
      ),
      commentary = matchInfo.comments,
      lineupsAvailable = None, // Boolean   // not available on LiveMatch
      currentMinute = None, // not available on LiveMatch
      currentPeriodStartTime = None,
      articleUrl = articleId.map(id => s"$mapiHost/items/$id")
    )

    val liveActivityEventType = triggeringEvent match {
      case CreateChannel(_) => CreateChannelEvent
      case StartLiveActivity(_) => StartLiveActivityEvent
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
