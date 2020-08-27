package football.lib

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

  private val generators: List[MatchEventGenerator] = List(fullTime, halfTime, secondHalf)

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
