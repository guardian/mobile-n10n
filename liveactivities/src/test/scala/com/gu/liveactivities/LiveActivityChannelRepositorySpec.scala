package com.gu.liveactivities

import com.gu.liveactivities.models.{FootballLiveActivity, LiveActivityMapping, RepositoryException}
import com.gu.liveactivities.service.LiveActivityChannelRepository
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
import software.amazon.awssdk.services.dynamodb.model._

import java.time.ZonedDateTime

class LiveActivityChannelRepositoryTest(implicit ev: ExecutionEnv)
    extends DynamodbSpecification
    with Mockito {

  override val TableName = "test-table"

  // This matcher adapts the existing `be_<=` matcher to a matcher applicable to `Any`
  def beEqualToExcludingMetaData(expected: LiveActivityMapping) = 
    beEqualTo(expected) ^^
    { (t: LiveActivityMapping) => t.copy(
        createdAt = expected.createdAt,
        lastModifiedAt = expected.lastModifiedAt
      )
    }

  "LiveActivityChannelRepository" should {

    "save a new channel mapping for an id if it does not exist" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0001")
      repository.createMapping(
        footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data).flatMap { _ =>
        repository.getMappingById(footballMapping.id)
      } must beEqualToExcludingMetaData(footballMapping).await
    }

    "Error if trying to save a new channel mapping for an id that already exists" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0004")
      repository.createMapping(footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data).flatMap { _ =>
        repository.createMapping(footballMapping.id, 
          footballMapping.channelId, 
          footballMapping.data)
      } must throwA[RepositoryException].await
    }

    "delete a channel mapping for an activity id if it exists" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0002")
      repository.createMapping(
        footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data).flatMap { _ =>
        repository.deleteMappingById(footballMapping.id)
      } must beEqualTo(()).await
    }

    "get a channel mapping for an activity id if it exists" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0003")
      repository.createMapping(
        footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data).flatMap { _ =>
        repository.getMappingById(footballMapping.id)
      } must beEqualToExcludingMetaData(footballMapping).await
    }
  }

  "LiveActivityChannelRepository.fetchAllMappingsByStatus" should {

    "return only mappings that are active, not live, and have a last event" in new RepositoryScope with ExampleData {
      val activeNotLiveWithEvent   = createFootballMappingWithId("status-0001").copy(isChannelActive = true, isLive = false, lastEventId = Some("event-1"), lastEventAt = Some(ZonedDateTime.now()))
      val activeNotLiveNoEvent     = createFootballMappingWithId("status-0002").copy(isChannelActive = true, isLive = false, lastEventId = None, lastEventAt = None)
      val activeLiveWithEvent      = createFootballMappingWithId("status-0003").copy(isChannelActive = true, isLive = true,  lastEventId = Some("event-3"), lastEventAt = Some(ZonedDateTime.now()))
      val inactiveNotLiveWithEvent = createFootballMappingWithId("status-0004").copy(isChannelActive = false, isLive = false, lastEventId = Some("event-4"), lastEventAt = Some(ZonedDateTime.now()))

      val setup = for {
        _ <- repository.createMapping(activeNotLiveWithEvent.id, activeNotLiveWithEvent.channelId, activeNotLiveWithEvent.data)
        _ <- repository.createMapping(activeNotLiveNoEvent.id, activeNotLiveNoEvent.channelId, activeNotLiveNoEvent.data)
        _ <- repository.createMapping(activeLiveWithEvent.id, activeLiveWithEvent.channelId, activeLiveWithEvent.data)
        _ <- repository.createMapping(inactiveNotLiveWithEvent.id, inactiveNotLiveWithEvent.channelId, inactiveNotLiveWithEvent.data)
        _ <- repository.updateMappingLastEvent(activeNotLiveWithEvent.id, Some("event-1"), Some(ZonedDateTime.now()))
        _ <- repository.updateMappingLiveAndLastEvent(activeLiveWithEvent.id, isLive = true, Some("event-3"), Some(ZonedDateTime.now()))
        _ <- repository.updateMappingLastEvent(inactiveNotLiveWithEvent.id, Some("event-4"), Some(ZonedDateTime.now()))
        _ <- repository.updateMappingActiveChannel(inactiveNotLiveWithEvent.id, isActive = false)
      } yield ()

      setup.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = true, isLive = false, hasLastEvent = true)
      } must (
        haveSize[List[LiveActivityMapping]](1) and
        contain((m: LiveActivityMapping) => m.id == "status-0001")
      ).await
    }

    "return only mappings that are active, not live, and have no last event" in new RepositoryScope with ExampleData {
      val activeNotLiveNoEvent   = createFootballMappingWithId("status-0010").copy(isChannelActive = true, isLive = false, lastEventId = None, lastEventAt = None)
      val activeNotLiveWithEvent = createFootballMappingWithId("status-0011").copy(isChannelActive = true, isLive = false)

      val setup = for {
        _ <- repository.createMapping(activeNotLiveNoEvent.id, activeNotLiveNoEvent.channelId, activeNotLiveNoEvent.data)
        _ <- repository.createMapping(activeNotLiveWithEvent.id, activeNotLiveWithEvent.channelId, activeNotLiveWithEvent.data)
        _ <- repository.updateMappingLastEvent(activeNotLiveWithEvent.id, Some("event-11"), Some(ZonedDateTime.now()))
      } yield ()

      setup.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = true, isLive = false, hasLastEvent = false)
      } must (
        haveSize[List[LiveActivityMapping]](1) and
        contain((m: LiveActivityMapping) => m.id == "status-0010")
      ).await
    }

    "return empty list when no mappings match the given status" in new RepositoryScope with ExampleData {
      val activeNotLiveNoEvent = createFootballMappingWithId("status-0020")

      repository.createMapping(activeNotLiveNoEvent.id, activeNotLiveNoEvent.channelId, activeNotLiveNoEvent.data).flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = false, isLive = true, hasLastEvent = true)
      } must beEqualTo(List.empty[LiveActivityMapping]).await
    }

    "return multiple matching mappings" in new RepositoryScope with ExampleData {
      val m1 = createFootballMappingWithId("status-0030")
      val m2 = createFootballMappingWithId("status-0031")
      val m3 = createFootballMappingWithId("status-0032")

      val setup = for {
        _ <- repository.createMapping(m1.id, m1.channelId, m1.data)
        _ <- repository.createMapping(m2.id, m2.channelId, m2.data)
        _ <- repository.createMapping(m3.id, m3.channelId, m3.data)
        _ <- repository.updateMappingLastEvent(m1.id, Some("event-30"), Some(ZonedDateTime.now()))
        _ <- repository.updateMappingLastEvent(m2.id, Some("event-31"), Some(ZonedDateTime.now()))
        // m3 gets no lastEvent so should not be returned
      } yield ()

      setup.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = true, isLive = false, hasLastEvent = true)
      } must (
        haveSize[List[LiveActivityMapping]](2) and
        contain((m: LiveActivityMapping) => m.id == "status-0030") and
        contain((m: LiveActivityMapping) => m.id == "status-0031") and
        not(contain((m: LiveActivityMapping) => m.id == "status-0032"))
      ).await
    }
  }

  trait RepositoryScope extends AsyncDynamoScope {
    val repository = new LiveActivityChannelRepository(asyncClient, TableName)
  }

  trait ExampleData {
    val footballData = FootballLiveActivity(
      homeTeam = "HomeTeamName",
      awayTeam = "AwayTeamName",
      competitionId = "test-competition-id",
      kickOffTimestamp = 1766433600
    )

    val footballMappingTemplate = LiveActivityMapping(
      id = "football-1234567",
      channelId = "test-channel-id",
      isChannelActive = true,
      isLive = false,
      data = Some(footballData),
      lastEventId = None,
      lastEventAt = None,
      createdAt = ZonedDateTime.now(),
      lastModifiedAt = ZonedDateTime.now(),
      ttlInEpochSeconds = Some(ZonedDateTime.now().toEpochSecond + 14 * 24 * 3600)
    )

    def createFootballMappingWithId(id: String): LiveActivityMapping = {
      footballMappingTemplate.copy(id = id)
    }
  }

  override def targetTableAttributes: Seq[(String, ScalarAttributeType)] = Seq(
    "id" -> S
  )
}
