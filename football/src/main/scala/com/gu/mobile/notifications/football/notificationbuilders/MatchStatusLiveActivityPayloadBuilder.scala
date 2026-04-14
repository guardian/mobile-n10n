package com.gu.mobile.notifications.football.notificationbuilders

import com.gu.mobile.notifications.client.models._
import com.gu.mobile.notifications.client.models.liveActitivites.{Competition, FootballLiveActivity, FootballMatchContentState, LiveActivityPayload, Scheduled, TeamState, UpdateLiveActivity}
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
//    val dismissals = ??? // todo needs to be calcualted from Dismissal events

    val dynamoData = ""

    // TODO this is hard coded mostly for now.
    val contentState = FootballMatchContentState(
      matchStatus = Scheduled,
      kickOffTimestamp = matchInfo.date.toEpochSecond, // included date and time
      homeTeam = TeamState(
        name = transformTeamName(matchInfo.homeTeam.name),
        score = score.home,
        logoAssetName = None, // tbc
        teamUrl = None, // tbc
        penaltyScore = None, // tbc
        redCards = None // tbc
      ),
      awayTeam = TeamState(
        name = transformTeamName(matchInfo.awayTeam.name),
        score = score.away,
        logoAssetName = None, // tbc
        teamUrl = None, // tbc
        penaltyScore = None, // tbc
        redCards = None // tbc
      ),
      competition = Competition(
        id = matchInfo.competition.map(_.id).getOrElse(""),
        name = matchInfo.competition.map(_.name).getOrElse("")
      ),
      commentary = matchInfo.comments,
      lineupsAvailable = None, // Booliean   // not available on LiveMatch
      currentMinute = None, // not available on LiveMatch
      currentPeriodStartTime = None,
      articleUrl = articleId.map(id => s"$mapiHost/items/$id")
    )

    val liveActivityEventType = UpdateLiveActivity // todo

    LiveActivityPayload(
      id =
        UUID.nameUUIDFromBytes(triggeringEvent.eventId.getBytes),
      eventType = liveActivityEventType, // start stop etc
      liveActivityType = FootballLiveActivity,
      liveActivityID =
        matchInfo.id, // Match ID in the case of football, tbc for other sports/events
      dynamoStoreData = Some(dynamoData), //  TBC
      broadcastContentStateData = Some(contentState),
      // todo
      eventTimestamp =
        new Date().getTime, // tbc - should this be the event time of the triggering event or the minute it happened??? ?
      topics = topics
    )

  }

  def transformTeamName(name: String): String = name.replace(" Ladies", "")

}
