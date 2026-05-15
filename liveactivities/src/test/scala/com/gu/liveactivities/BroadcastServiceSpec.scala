package com.gu.liveactivities

import com.gu.liveactivities.models.{LiveActivityData, LiveActivityMapping}
import com.gu.liveactivities.service.{BroadcastApiClient, ChannelMappingsRepository}
import com.gu.liveactivities.util.DateTimeHelper
import com.gu.mobile.notifications.client.models.liveActitivites._
import org.slf4j
import org.slf4j.Logger
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class BroadcastServiceSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {

  trait Context extends Scope{
    val channelManagerRepositoryMock: ChannelMappingsRepository = mock[ChannelMappingsRepository]
    val broadcastApiClientMock: BroadcastApiClient = mock[BroadcastApiClient]
    val mockLogger: Logger = mock[org.slf4j.Logger]

    val broadcastService: BroadcastService = new BroadcastService(channelManagerRepositoryMock, broadcastApiClientMock)(ee.ec) {
      override val logger: slf4j.Logger = mockLogger
    }
  }
  val now = ZonedDateTime.now()
  val matchId = "matchId1"
  val defaultContentState = FootballMatchContentState(
    matchStatus = FirstHalf,
    kickOffTimestamp = now.toEpochSecond - 1800, // 30min ago
    homeTeam = TeamState(name = "Arsenal", score = 1),
    awayTeam = TeamState(name = "Chelsea", score = 0),
    competition = Competition(id = "100", name = "Premier League", round = Some("League")),
    commentary = Some("Arsenal dominating."),
    currentMinute = Some(30),
    articleUrl = None,
    matchInfoUrl = "http://www.theguardian.com/football/match/some-match-id"
  )
  val defaultPayload = new LiveActivityPayload(
    id = UUID.nameUUIDFromBytes("test-match-event".getBytes),
    eventType = UpdateLiveActivityEvent,
    liveActivityType = FootballLiveActivity,
    liveActivityID = matchId,
    dynamoStoreData = None,
    broadcastContentStateData =  Some(defaultContentState),
    eventTimestamp = now.toEpochSecond - 60L,  // 1min ago
  )

  val liveActivityMappingAtCreateChannelState = LiveActivityMapping(
    id = matchId,
    channelId = "channelId1",
    isChannelActive = true,
    isLive = false,
    lastEventId = None,
    lastEventAt = None,
    createdAt = now.minusHours(1),
    lastModifiedAt = now.minusHours(1),
    ttlInEpochSeconds = Some(now.toEpochSecond + 14 * 24 * 3600),
    data = Some(LiveActivityData.toLiveActivityData(defaultContentState))
  )

  "BroadcastService.processBroadcast" should {
    "return a failed future when there is no mapping in dynamo for given liveActivityID" in new Context {
      // Setup
      channelManagerRepositoryMock.getMappingById("matchId1") returns Future.failed(new RuntimeException("No mapping found"))

      // Execute
      val broadcastFuture: Future[String] = broadcastService.processBroadcast(defaultPayload, shouldEndBroadcast = false, defaultContentState)

      // Verify
      broadcastFuture.failed.map(_.getMessage) must beEqualTo("No mapping found").await
    }

    "not process broadcast but return a successful future given the live activity has ended" in new Context{
      // Setup
      val endedLiveActivityMapping: LiveActivityMapping = liveActivityMappingAtCreateChannelState.copy(
        isLive = false,
        lastEventId = Some("someEventId"),
        lastEventAt = Some(now.minusMinutes(5))
      )
      channelManagerRepositoryMock.getMappingById("matchId1") returns Future.successful(endedLiveActivityMapping)

      // Execute
      val broadcastResult: String = Await.result(broadcastService.processBroadcast(defaultPayload, shouldEndBroadcast = false, defaultContentState), 5.seconds)

      // Verify
      eventually {
        there was one(mockLogger).warn(s"broadcast-update event ID ${defaultPayload.id.toString} not allowed after broadcast-end for match ID $matchId")
      }
      broadcastResult must beEqualTo("channelId1")
    }

    "return a failed future given the channel is not active anymore" in new Context {
      // Setup
      val inActiveLiveActivityMapping: LiveActivityMapping = liveActivityMappingAtCreateChannelState.copy(
        isChannelActive = false,
      )
      channelManagerRepositoryMock.getMappingById(matchId) returns Future.successful(inActiveLiveActivityMapping)

      // Execute
      val broadcastFuture: Future[String] = broadcastService.processBroadcast(defaultPayload, shouldEndBroadcast = false, defaultContentState)

      // Verify
      eventually {
        there was one(mockLogger).error(s"Channel not active for match ID $matchId")
      }
      broadcastFuture.failed.map(_.getMessage) must beEqualTo(s"Live activity invalid state for id $matchId: Channel not active").await
    }

    "return a failed future given the event id is the same as the last event id" in new Context {
      // Setup
      val duplicateLiveActivityMapping: LiveActivityMapping = liveActivityMappingAtCreateChannelState.copy(
        isLive = true,
        lastEventId = Some(defaultPayload.id.toString)
      )
      channelManagerRepositoryMock.getMappingById(matchId) returns Future.successful(duplicateLiveActivityMapping)

      // Execute
      val broadcastFuture: Future[String] = broadcastService.processBroadcast(defaultPayload, shouldEndBroadcast = false, defaultContentState)

      // Verify
      eventually {
        there was one(mockLogger).warn(s"Duplicate event ID ${defaultPayload.id.toString} for match ID $matchId")
      }
      broadcastFuture.failed.map(_.getMessage) must beEqualTo(s"Live activity invalid state for id $matchId: Duplicate event ID").await
    }

    "return a failed future given the current event time is before the last event time" in new Context {
      // Setup
      val existingLiveActivityMapping: LiveActivityMapping = liveActivityMappingAtCreateChannelState.copy(
        isLive = true,
        lastEventId = Some("someEventId2"),
        lastEventAt = Some(now.minusMinutes(1))
      )
      val outOfOrderPayload: LiveActivityPayload = defaultPayload.copy(
        eventTimestamp = now.toEpochSecond - 10L // 10 seconds ago, which is before the lastEventAt of the mapping
      )
      channelManagerRepositoryMock.getMappingById(matchId) returns Future.successful(existingLiveActivityMapping)

      // Execute
      val broadcastFuture: Future[String] = broadcastService.processBroadcast(outOfOrderPayload, shouldEndBroadcast = false, defaultContentState)
      val eventZonedDateTimeStr: String = DateTimeHelper.dateTimeToString(DateTimeHelper.dateTimeFromLong(outOfOrderPayload.eventTimestamp))

      // Verify
      eventually {
        there was one(mockLogger).warn(s"Out of order event time $eventZonedDateTimeStr for match ID $matchId")
      }
      broadcastFuture.failed.map(_.getMessage) must beEqualTo(s"Live activity invalid state for id $matchId: Out of order event time").await
    }
  }
}
