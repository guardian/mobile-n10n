package com.gu.mobile.notifications.football.lib

import java.util.UUID
import com.gu.mobile.notifications.client.models.liveActitivites._
import com.gu.mobile.notifications.football.lib.DynamoDistinctCheck.{Distinct, Duplicate}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future

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
  }
}

