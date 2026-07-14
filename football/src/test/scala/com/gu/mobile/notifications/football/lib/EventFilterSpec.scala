package com.gu.mobile.notifications.football.lib

import java.util.UUID
import com.gu.mobile.notifications.client.models.liveActitivites._
import com.gu.mobile.notifications.football.lib.DynamoDistinctCheck.{Distinct, Duplicate}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.{ExecutionContext, Future}

class EventFilterSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {

  trait FilterScopeLiveActivities extends Scope {
    val distinctCheck = mock[DynamoDistinctCheck[LiveActivityPayload, DynamoMatchLiveActivity]]
    val eventFilter = new EventFilter[LiveActivityPayload, DynamoMatchLiveActivity](distinctCheck)

    def makePayload(eventType: LiveActivityEventType, matchId: String): LiveActivityPayload =
      LiveActivityPayload(
        id = UUID.randomUUID(),
        eventType = eventType,
        liveActivityType = FootballLiveActivity,
        liveActivityID = matchId,
        dynamoStoreData = None,
        broadcastContentStateData = None,
        eventTimestamp = System.currentTimeMillis() / 1000
      )
  }

  trait FilterScopeWithStateDiffer extends FilterScopeLiveActivities {
    val matchStateDiffer = mock[DynamoMatchStateDiffer]
    override val eventFilter = new EventFilter[LiveActivityPayload, DynamoMatchLiveActivity](distinctCheck, Some(matchStateDiffer))

    def footballState(matchId: String): FootballMatchContentState =
      FootballMatchContentState(
        matchStatus = FirstHalf,
        kickOffTimestamp = 0L,
        homeTeam = TeamState(id = "1", name = "Arsenal"),
        awayTeam = TeamState(id = "2", name = "Chelsea"),
        competition = Competition(id = "100", name = "Premier League"),
        matchInfoUrl = s"https://www.theguardian.com/football/match/$matchId"
      )

    def makeStateChangePayload(matchId: String, state: FootballMatchContentState): LiveActivityPayload =
      makePayload(UpdateStateChangeLiveActivityEvent, matchId).copy(broadcastContentStateData = Some(state))
  }

  "filterDynamoEventsForLiveActivities" should {

    "filter out duplicate events" in new FilterScopeLiveActivities {
      val event1 = makePayload(UpdateLiveActivityEvent, "match-1")
      val event2 = makePayload(UpdateLiveActivityEvent, "match-2")

      distinctCheck.isDuplicate(event1) returns Future.successful(true)
      distinctCheck.isDuplicate(event2) returns Future.successful(false)
      distinctCheck.insertEvent(event2) returns Future.successful(Distinct)

      eventFilter.filterDynamoEventsForLiveActivities(List(event1, event2)) must contain(exactly(event2)).await
    }

    "filter out events where insertEvent returns Duplicate" in new FilterScopeLiveActivities {
      val event1 = makePayload(UpdateLiveActivityEvent, "match-1")
      val event2 = makePayload(UpdateLiveActivityEvent, "match-2")

      distinctCheck.isDuplicate(event1) returns Future.successful(false)
      distinctCheck.isDuplicate(event2) returns Future.successful(false)
      distinctCheck.insertEvent(event1) returns Future.successful(Duplicate)
      distinctCheck.insertEvent(event2) returns Future.successful(Distinct)

      eventFilter.filterDynamoEventsForLiveActivities(List(event1, event2)) must contain(exactly(event2)).await
    }

    "exclude end event when an update event exists for the same match" in new FilterScopeLiveActivities {
      val updateEvent = makePayload(UpdateLiveActivityEvent, "match-1")
      val endEvent = makePayload(EndLiveActivityEvent, "match-1")

      distinctCheck.isDuplicate(updateEvent) returns Future.successful(false)
      distinctCheck.isDuplicate(endEvent) returns Future.successful(false)
      distinctCheck.insertEvent(updateEvent) returns Future.successful(Distinct)

      val result = eventFilter.filterDynamoEventsForLiveActivities(List(updateEvent, endEvent))
      result must contain(exactly(updateEvent)).await
    }

    "allow end event through when received in isolation (no update for same match)" in new FilterScopeLiveActivities {
      val endEvent = makePayload(EndLiveActivityEvent, "match-1")

      distinctCheck.isDuplicate(endEvent) returns Future.successful(false)
      distinctCheck.insertEvent(endEvent) returns Future.successful(Distinct)

      eventFilter.filterDynamoEventsForLiveActivities(List(endEvent)) must contain(exactly(endEvent)).await
    }

    "allow end event through when update is for a different match" in new FilterScopeLiveActivities {
      val updateEvent = makePayload(UpdateLiveActivityEvent, "match-2")
      val endEvent = makePayload(EndLiveActivityEvent, "match-1")

      distinctCheck.isDuplicate(updateEvent) returns Future.successful(false)
      distinctCheck.isDuplicate(endEvent) returns Future.successful(false)
      distinctCheck.insertEvent(updateEvent) returns Future.successful(Distinct)
      distinctCheck.insertEvent(endEvent) returns Future.successful(Distinct)

      val result = eventFilter.filterDynamoEventsForLiveActivities(List(updateEvent, endEvent))
      result must contain(exactly(updateEvent, endEvent)).await
    }

    "return empty list when all events are duplicates" in new FilterScopeLiveActivities {
      val event1 = makePayload(UpdateLiveActivityEvent, "match-1")
      val event2 = makePayload(UpdateLiveActivityEvent, "match-2")

      distinctCheck.isDuplicate(event1) returns Future.successful(true)
      distinctCheck.isDuplicate(event2) returns Future.successful(true)

      eventFilter.filterDynamoEventsForLiveActivities(List(event1, event2)) must beEmpty[List[LiveActivityPayload]].await
    }

    "use local cache to skip dynamo call on second invocation" in new FilterScopeLiveActivities {
      val event1 = makePayload(UpdateLiveActivityEvent, "match-1")

      // First call: event passes through isDuplicate and insertEvent
      distinctCheck.isDuplicate(event1) returns Future.successful(false)
      distinctCheck.insertEvent(event1) returns Future.successful(Distinct)

      eventFilter.filterDynamoEventsForLiveActivities(List(event1)) must contain(exactly(event1)).await

      // Second call: event is now in local cache, isDuplicate should NOT be called again
      eventFilter.filterDynamoEventsForLiveActivities(List(event1)) must beEmpty[List[LiveActivityPayload]].await

      // isDuplicate should only have been called once (from the first invocation)
      there was one(distinctCheck).isDuplicate(event1)
    }
  }


  // TODO copilot review
  "filterDynamoEventsForLiveActivities persisting match state" should {

    "persist the broadcast state and emit the event for a state-change event" in new FilterScopeWithStateDiffer {
      val state = footballState("match-1")
      val stateChangeEvent = makeStateChangePayload("match-1", state)

      distinctCheck.isDuplicate(stateChangeEvent) returns Future.successful(false)
      distinctCheck.insertEvent(stateChangeEvent) returns Future.successful(Distinct)
      matchStateDiffer.updateState(any[String], any[FootballMatchContentState])(any[ExecutionContext]) returns Future.unit

      eventFilter.filterDynamoEventsForLiveActivities(List(stateChangeEvent)) must contain(exactly(stateChangeEvent)).await
      there was one(matchStateDiffer).updateState(===("match-1"), ===(state))(any[ExecutionContext])
    }

    "not call updateState for a non state-change event" in new FilterScopeWithStateDiffer {
      val updateEvent = makePayload(UpdateLiveActivityEvent, "match-1")

      distinctCheck.isDuplicate(updateEvent) returns Future.successful(false)
      distinctCheck.insertEvent(updateEvent) returns Future.successful(Distinct)

      eventFilter.filterDynamoEventsForLiveActivities(List(updateEvent)) must contain(exactly(updateEvent)).await
      there was no(matchStateDiffer).updateState(any[String], any[FootballMatchContentState])(any[ExecutionContext])
    }

    "skip emitting a state-change event when the state write fails" in new FilterScopeWithStateDiffer {
      val state = footballState("match-1")
      val stateChangeEvent = makeStateChangePayload("match-1", state)

      distinctCheck.isDuplicate(stateChangeEvent) returns Future.successful(false)
      distinctCheck.insertEvent(stateChangeEvent) returns Future.successful(Distinct)
      matchStateDiffer.updateState(any[String], any[FootballMatchContentState])(any[ExecutionContext]) returns Future.failed(new RuntimeException("dynamo down"))

      eventFilter.filterDynamoEventsForLiveActivities(List(stateChangeEvent)) must beEmpty[List[LiveActivityPayload]].await
    }
  }
}
