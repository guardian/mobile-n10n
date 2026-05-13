package com.gu.liveactivities

import com.gu.liveactivities.models.LiveActivityMapping
import com.gu.liveactivities.service.{ChannelApiClient, ChannelMappingsRepository}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import java.time.ZonedDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class ChannelCleanUpLambdaTest extends Specification with Mockito {

  implicit val ec: ExecutionContext = ExecutionContext.global

  "ChannelCleanUpService.deleteChannelsForEndedBroadcasts" should {

    "close channels no longer live but skip channels created within the last 7 days" in {

      val oldMapping = LiveActivityMapping(
        id = "match-old",
        channelId = "channel-old",
        isChannelActive = true,
        isLive = false,
        data = None,
        lastEventId = Some("event-1"),
        lastEventAt = None,
        createdAt = ZonedDateTime.now().minusDays(10), // older than 7 days → should be closed
        lastModifiedAt = ZonedDateTime.now().minusDays(10),
        ttlInEpochSeconds = None,
      )

      val recentMapping = LiveActivityMapping(
        id = "match-recent",
        channelId = "channel-recent",
        isChannelActive = true,
        isLive = false,
        data = None,
        lastEventId = Some("event-2"),
        lastEventAt = None,
        createdAt = ZonedDateTime.now().minusDays(1), // within 7 days → should be skipped
        lastModifiedAt = ZonedDateTime.now().minusDays(1),
        ttlInEpochSeconds = None,
      )

      val mockRepository = mock[ChannelMappingsRepository]
      val mockChannelApiClient = mock[ChannelApiClient]

      mockRepository.fetchAllMappingsByStatus(
        isChannelActive = true,
        isLive = false,
        hasLastEvent = true,
      ) returns Future.successful(List(oldMapping, recentMapping))

      mockRepository.updateMappingActiveChannel(any[String], any[Boolean]) returns Future.successful(())
      mockChannelApiClient.closeChannel(any[String]) returns Future.successful(())

      val service = new ChannelCleanUpService(mockRepository, mockChannelApiClient)

      val result = Await.result(service.deleteChannelsForEndedBroadcasts(), 5.seconds)
      result must haveSize(2)
      // old channel: closeChannel should be called once
      there was one(mockChannelApiClient).closeChannel("channel-old")

      // recent channel: closeChannel should NOT be called
      there was no(mockChannelApiClient).closeChannel("channel-recent")
    }
  }
}
