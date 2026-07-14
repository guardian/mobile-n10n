package com.gu.mobile.notifications.football.lib

import java.time.ZonedDateTime
import java.util.UUID

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{MatchDay, MatchDayTeam, Round, Stage}

class SyntheticMatchEventGeneratorSpec extends Specification {

  trait TestScope extends Scope {
    val home = MatchDayTeam("1", "Liverpool", None, None, None, None)
    val away = MatchDayTeam("2", "Plymouth", None, None, None, None)


    val matchInfo = MatchDay(
      id = "some-match-id",
      date = ZonedDateTime.parse("2000-01-01T00:00:00Z"),
      competition = None,
      stage = Stage("1"),
      round = Round("1", None),
      leg = "home",
      liveMatch = true,
      result =  false,
      previewAvailable = false,
      reportAvailable = false,
      lineupsAvailable = false,
      matchStatus = "FH",
      attendance = None,
      homeTeam = home,
      awayTeam = away,
      referee = None,
      venue = None,
      comments = None
    )

    val timelineEvent = pa.MatchEvent(
      id = None,
      teamID = None,
      eventType = "timeline",
      matchTime = Some("0:00"),
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

    val kickoffId = UUID.nameUUIDFromBytes(s"football-match/match-id/timeline/00:00".getBytes).toString
    val currentTime = () => ZonedDateTime.now()
  }

  "A SyntheticMatchEvent generator" should {
    "Add id to first timeline event" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo, stateChange = false) mustEqual List(timelineEvent.copy(id = Some(kickoffId)))
    }
    "Add half-time event if status is HT" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "HT"), stateChange = false).drop(1).head.eventType mustEqual "half-time"
    }
    "Add second-half event if status is SHS" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "SHS"), stateChange = false).drop(1).head.eventType mustEqual "second-half"
    }
    "Add full-time event if match is result" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(result = true, liveMatch = true), stateChange = false).drop(1).head.eventType mustEqual "full-time"
    }

    "Add extra time event if status is ETS" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "ETS"), stateChange = false).drop(1).head.eventType mustEqual "extra-time-first-half"
    }

    "Add extra time half time event if status is ETHT" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "ETHT"), stateChange = false).drop(1).head.eventType mustEqual "extra-time-half-time"
    }

    "Add extra time second half event if status is ETSHS" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "ETSHS"), stateChange = false).drop(1).head.eventType mustEqual "extra-time-second-half"
    }

    "Add penalty shootout event if status is PT" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "PT"), stateChange = false).drop(1).head.eventType mustEqual "penalties"
    }

    "Add extra-time-to-be-played event if status is FTET" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "FTET"), stateChange = false).drop(1).head.eventType mustEqual "extra-time-to-be-played"
    }

    "Add penalties-to-be-played event if status is FTPT" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "FTPT"), stateChange = false).drop(1).head.eventType mustEqual "penalties-to-be-played"
    }

    "Add penalties-to-be-played event if status is ETFTPT" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "ETFTPT"), stateChange = false).drop(1).head.eventType mustEqual "penalties-to-be-played"
    }

    "Add suspended event if status is Suspended" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "Suspended"), stateChange = false).drop(1).head.eventType mustEqual "suspended"
    }

    "Add resumed event if status is Resumed" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "Resumed"), stateChange = false).drop(1).head.eventType mustEqual "resumed"
    }

    "Add abandoned event if status is Abandoned" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "Abandoned"), stateChange = false).drop(1).head.eventType mustEqual "abandoned"
    }

    "Add postponed event if status is Postponed" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "Postponed"), stateChange = false).drop(1).head.eventType mustEqual "postponed"
    }

    "Add cancelled event if status is Cancelled" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "Cancelled"), stateChange = false).drop(1).head.eventType mustEqual "cancelled"
    }

  }

  "A SyntheticMatchEvent generator supporting Live Activities" should {
    "Add a createChannel event if match kick off is within 2 hrs" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(), "match-id", matchInfo.copy(date = ZonedDateTime.now.plusHours(1)), stateChange = false).head.eventType mustEqual "create-channel"
    }

    "Add a startLiveActivity event if match kick off is within 20min" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(), "match-id", matchInfo.copy(date = ZonedDateTime.now.plusMinutes(15)), stateChange = false).map(_.eventType) must contain("start-live-activity")
    }

    "Add id to first timeline event" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(date = ZonedDateTime.now()), stateChange = false) mustEqual List(timelineEvent.copy(id = Some(kickoffId)))
    }

    "Add an endLiveActivity event if match info contains result" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(result = true, liveMatch = false), stateChange = false).reverse.head.eventType mustEqual "end-live-activity"
    }

    "Add a pre-match event if match kick off is within 20min" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      val events = generator.generate(List(), "match-id", matchInfo.copy(date = ZonedDateTime.now.plusMinutes(15)), stateChange = false)
      events.map(_.eventType) must contain("pre-match")
    }
  }

  "A SyntheticMatchEvent generator supporting StateChangeEvents" should {
    "Create a state-change event when a state change has occurred" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      val events = generator.generate(List(timelineEvent), "match-id", matchInfo, stateChange = true)
      events.map(_.eventType) must contain("state-change")
    }

    "Not create a state-change event when no state change has occurred" in new TestScope {
      val generator = new SyntheticMatchEventGenerator(currentTime)
      val events = generator.generate(List(timelineEvent), "match-id", matchInfo, stateChange = false)
      events.map(_.eventType) must not contain("state-change")
    }

  }
}
