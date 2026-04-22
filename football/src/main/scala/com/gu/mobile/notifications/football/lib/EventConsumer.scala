package com.gu.mobile.notifications.football.lib

import com.gu.mobile.notifications.client.models.NotificationPayload
import com.gu.mobile.notifications.client.models.liveActitivites.LiveActivityPayload
import com.gu.mobile.notifications.football.Logging
import com.gu.mobile.notifications.football.models.{FootballMatchEvent, MatchDataWithArticle}
import com.gu.mobile.notifications.football.notificationbuilders.{
  MatchStatusLiveActivityPayloadBuilder,
  MatchStatusNotificationBuilder
}
import pa.{MatchDay, MatchEvent}

class EventConsumer(
  matchStatusNotificationBuilder: MatchStatusNotificationBuilder
) extends Logging {

  def eventsToNotifications(matchData: MatchDataWithArticle): List[NotificationPayload] = {
    matchData.allEvents.flatMap { event =>
      receiveEvent(matchData.matchDay, matchData.allEvents, event, matchData.articleId)
    }
  }
  def receiveEvent(matchDay: MatchDay, events: List[MatchEvent], event: MatchEvent, articleId: Option[String]): List[NotificationPayload] = {
    logger.debug(s"Processing event $event for match ${matchDay.id}")
    val previousEvents = events.takeWhile(_ != event)
    FootballMatchEvent.fromPaMatchEvent(matchDay.homeTeam, matchDay.awayTeam)(event) map { ev =>
      List(matchStatusNotificationBuilder.build(
        triggeringEvent = ev,
        matchInfo = matchDay,
        previousEvents = previousEvents.flatMap(FootballMatchEvent.fromPaMatchEvent(matchDay.homeTeam, matchDay.awayTeam)(_)),
        articleId = articleId
      ))
    } getOrElse Nil
  }
}

class LiveActivityEventConsumer(matchStatusLiveActivityPayloadBuilder: MatchStatusLiveActivityPayloadBuilder) extends Logging {

  def eventsToLiveActivityPayload(
    matchData: MatchDataWithArticle
  ): List[LiveActivityPayload] = {
    matchData.allEvents.flatMap { event =>
      processForLiveActivities(
        matchData.matchDay,
        matchData.allEvents,
        event,
        matchData.articleId
      )
    }
  }

  def processForLiveActivities(
    matchDay: MatchDay,
    events: List[MatchEvent],
    event: MatchEvent,
    articleId: Option[String]
  ): List[LiveActivityPayload] = {
    logger.debug(
      s"Processing live activity event $event for match ${matchDay.id}"
    )

    val previousEvents = events.takeWhile(_ != event)

    FootballMatchEvent.fromPaMatchEvent(matchDay.homeTeam, matchDay.awayTeam)(
      event
    ) map { ev =>
      // Build live activity payload
      List(
        matchStatusLiveActivityPayloadBuilder.build(
          triggeringEvent = ev,
          matchInfo = matchDay,
          previousEvents = previousEvents.flatMap(
            FootballMatchEvent
              .fromPaMatchEvent(matchDay.homeTeam, matchDay.awayTeam)(_)
          ),
          articleId = articleId
        )
      )
    } getOrElse Nil
  }
}