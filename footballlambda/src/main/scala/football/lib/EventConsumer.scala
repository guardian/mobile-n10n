package football.lib

import com.gu.mobile.notifications.client.models.NotificationPayload
import football.Logging
import football.models.{FootballMatchEvent, MatchDataWithArticle}
import football.notificationbuilders.MatchStatusNotificationBuilder
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
