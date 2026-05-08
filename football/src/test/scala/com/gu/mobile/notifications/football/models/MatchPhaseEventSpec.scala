package com.gu.mobile.notifications.football.models

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class MatchPhaseEventSpec extends Specification with Mockito {
  "MatchPhaseEvent" should {
    "Create a Kickoff event from timeline event 0:00" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "timeline"
      event.matchTime returns Some("0:00")
      event.eventTime returns Some("0")
      MatchPhaseEvent.fromEvent(event) should beSome(KickOff("123"))
    }
    "Return none event for timeline event 0:01" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "timeline"
      event.matchTime returns Some("0:01")
      event.eventTime returns Some("1")
      MatchPhaseEvent.fromEvent(event) should beNone
    }
    "Create a fulltime event from full-time" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "full-time"
      event.eventTime returns Some("92")
      MatchPhaseEvent.fromEvent(event) should beSome(FullTime("123"))
    }
    "Create a halftime event from half-time" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "half-time"
      event.eventTime returns Some("45")
      MatchPhaseEvent.fromEvent(event) should beSome(HalfTime("123"))
    }
    "Create a secondhalf event from second-half" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "second-half"
      event.eventTime returns Some("47")
      MatchPhaseEvent.fromEvent(event) should beSome(SecondHalf("123"))
    }

    "Create an extra time first half phase event from extra-time-first-half" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "extra-time-first-half"
      event.eventTime returns Some("99")
      MatchPhaseEvent.fromEvent(event) should beSome(ExtraTimeFirstHalf("123"))
    }

    "Create an extra time half-time phase event from extra-time-half-time" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "extra-time-half-time"
      event.eventTime returns Some("105")
      MatchPhaseEvent.fromEvent(event) should beSome(ExtraTimeHalfTime("123"))
    }

    "Create an extra time second half phase event from extra-time-second-half" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "extra-time-second-half"
      event.eventTime returns Some("115")
      MatchPhaseEvent.fromEvent(event) should beSome(ExtraTimeSecondHalf("123"))
    }

    "Create a penalty phase event from penalties" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "penalties"
      event.eventTime returns Some("120")
      MatchPhaseEvent.fromEvent(event) should beSome(Penalties("123"))
    }


    // Live Activity Phase events

    "Create a create-channel event from create-channel" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "create-channel"
      MatchPhaseEvent.fromEvent(event) should beSome(CreateChannel("123"))
    }
    "Create a start-live-activity event from start-live-activity" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "start-live-activity"
      event.eventTime returns Some("0")
      MatchPhaseEvent.fromEvent(event) should beSome(StartLiveActivity("123"))
    }
    "Create an end-live-activity event from end-live-activity" in {
      val event = mock[pa.MatchEvent]
      event.id returns Some("123")
      event.eventType returns "end-live-activity"
      event.eventTime returns Some("90")
      MatchPhaseEvent.fromEvent(event) should beSome(EndLiveActivity("123"))
    }





  }
}
