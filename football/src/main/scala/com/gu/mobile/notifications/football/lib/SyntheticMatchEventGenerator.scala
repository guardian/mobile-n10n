package com.gu.mobile.notifications.football.lib

import java.util.UUID

import pa.{MatchDay, MatchEvent}

class SyntheticMatchEventGenerator {

  def generate(events: List[MatchEvent], id: String, matchDay: MatchDay): List[MatchEvent] = {
    events.map(enhanceTimelineEvents(id)) ++ generators.flatMap(_.apply(matchDay, events)) // order is important here
  }

  private type MatchEventGenerator = (MatchDay, List[pa.MatchEvent]) => Option[MatchEvent]

  private val fullTime: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.result) Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/full-time".getBytes).toString),
      eventType = "full-time"
    ))
    else None
  }

  private val halfTime: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "HT") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/half-time".getBytes).toString),
      eventType = "half-time"
    ))
    else None
  }

  private val secondHalf: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "SHS") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/second-half".getBytes).toString),
      eventType = "second-half"
    ))
    else None
  }

  // Live Activity supporting events //

  val now: Long = java.time.Instant.now.getEpochSecond
  def koWithinTwoHours(ko: Long): Boolean = now >= ko - 7200 && now < ko - 1200
  def koWithin20Minutes(ko: Long): Boolean = now >= ko - 1200 && now < ko

  private val createChannel: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (koWithinTwoHours(matchDay.date.toEpochSecond)) Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/create-channel".getBytes).toString),
      eventType = "create-channel"
    ))
    else None
  }

  private val startLiveActivity: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (koWithin20Minutes(matchDay.date.toEpochSecond)) Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/start-live-activity".getBytes).toString),
      eventType = "start-live-activity"
    ))
    else None
  }

  // Note: matches may be abandoned after kick off with no result, in which case rely on "stale-date" to end the activity (4hrs)
  // todo can we just use full-time event above?
  private val endLiveActivity: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.result) Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/end-live-activity".getBytes).toString),
      eventType = "end-live-activity"
    ))
    else None
  }

  private val generators: List[MatchEventGenerator] = List(fullTime, halfTime, secondHalf, createChannel, startLiveActivity, endLiveActivity)

  private def emptyMatchEvent = MatchEvent(
    id = None,
    teamID = None,
    eventType = "",
    matchTime = None,
    eventTime = None,
    addedTime = None,
    players = List.empty,
    reason = None,
    how = None,
    whereFrom = None,
    whereTo = None,
    distance = None,
    outcome = None
  )

  private def enhanceTimelineEvents(matchId: String)(event: MatchEvent): MatchEvent = {
    if (event.eventType == "timeline" && event.matchTime.contains("0:00")) {
      event.copy(id = Some(UUID.nameUUIDFromBytes(s"football-match/$matchId/timeline/00:00".getBytes).toString))
    } else {
      event
    }
  }
}
