package com.gu.mobile.notifications.football.lib

import java.util.UUID
import pa.{MatchDay, MatchEvent}

import java.time.{Instant, ZoneId, ZonedDateTime}

// Synthetic events create a timeline event for a match status change, that can be processed by the EventConsumer
// and transformed into a NotificationPayload and/or LiveActivityPayload for broadcasting.

class SyntheticMatchEventGenerator(getCurrentTime: () => ZonedDateTime) {

  def generate(events: List[MatchEvent], id: String, matchDay: MatchDay): List[MatchEvent] = {
    // Live activity synthetic events are appended at the end, but when they are first generated, no other timeline events exist and duplicate events are filtered so this should not matter.
    // todo This needs to be verified e2e
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


  // LIVE ACTIVITIES
  private val suspended: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "Suspended") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/suspended".getBytes).toString),
      eventType = "suspended"
    ))
    else None
  }

  private val resumed: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "Resumed") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/resumed".getBytes).toString),
      eventType = "resumed"
    ))
    else None
  }

  private val abandoned: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "Abandoned") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/abandoned".getBytes).toString),
      eventType = "abandoned"
    ))
    else None
  }

  private val postponed: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "Postponed") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/postponed".getBytes).toString),
      eventType = "postponed"
    ))
    else None
  }

  private val cancelled: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "Cancelled") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/cancelled".getBytes).toString),
      eventType = "cancelled"
    ))
    else None
  }

  // match statuses synthetic events needed for live activities
  private val extraTimeToBePlayed: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "FTET") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/extra-time-to-be-played".getBytes).toString),
      eventType = "extra-time-to-be-played"
    ))
    else None
  }

  private val extraTimeFirstHalf: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "ETS") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/extra-time-first-half".getBytes).toString),
      eventType = "extra-time-first-half"
    ))
    else None
  }

  private val extraTimeHalfTime: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "ETHT") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/extra-time-half-time".getBytes).toString),
      eventType = "extra-time-half-time"
    ))
    else None
  }

  private val extraTimeSecondHalf: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "ETSHS") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/extra-time-second-half".getBytes).toString),
      eventType = "extra-time-second-half"
    ))
    else None
  }

  private val penaltiesToBePlayed: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "FTPT" || matchDay.matchStatus == "ETFTPT") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/penalties-to-be-played".getBytes).toString),
      eventType = "penalties-to-be-played"
    ))
    else None
  }

  private val penalties: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (matchDay.matchStatus == "PT") Some(emptyMatchEvent.copy(
      id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/penalties".getBytes).toString),
      eventType = "penalties"
    ))
    else None
  }

  // Live Activity supporting events //

  def now: Long = getCurrentTime().toInstant.getEpochSecond
  def koWithinTwoHours(ko: Long): Boolean = now >= ko - 7200 && now < ko - 1200
  def koWithin20Minutes(ko: Long): Boolean = now >= ko - 1200 && now < ko

  private val createChannel: MatchEventGenerator = { (matchDay: MatchDay, matchEvents: List[pa.MatchEvent]) =>
    if (koWithinTwoHours(matchDay.date.toEpochSecond)) {
      Some(emptyMatchEvent.copy(
        id = Some(UUID.nameUUIDFromBytes(s"football-match/${matchDay.id}/create-channel".getBytes).toString),
        eventType = "create-channel"
        )
      )
    } else None
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

  private val generators: List[MatchEventGenerator] = List(
    fullTime,
    halfTime,
    secondHalf,
    extraTimeToBePlayed,
    extraTimeFirstHalf,
    extraTimeHalfTime,
    extraTimeSecondHalf,
    penaltiesToBePlayed,
    penalties,
    suspended,
    resumed,
    abandoned,
    postponed,
    cancelled,
    createChannel,
    startLiveActivity,
    endLiveActivity)

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
