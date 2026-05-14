package com.gu.liveactivities

import com.gu.liveactivities.models.{FootballLiveActivity, LiveActivityMapping, RepositoryException}
import com.gu.liveactivities.service.LiveActivityChannelRepository
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
import software.amazon.awssdk.services.dynamodb.model._

import java.time.ZonedDateTime
import scala.concurrent.Future

class LiveActivityChannelRepositoryTest(implicit ev: ExecutionEnv)
    extends DynamodbSpecification
    with Mockito {

  override protected def before: Any = ()
  override protected def after: Any = ()
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

    "return only mappings that are active, not live, and have a last event" in new FetchStatusScope {
      prepareData.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = true, isLive = false, hasLastEvent = true)
      } must (
        haveSize[List[LiveActivityMapping]](1) and
          contain((m: LiveActivityMapping) => m.id must beEqualTo(activeNotLiveWithEvent.id))
        ).await
    }

    "return only mappings that are active, not live, and have no last event" in new FetchStatusScope {
      prepareData.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = true, isLive = false, hasLastEvent = false)
      } must (
        haveSize[List[LiveActivityMapping]](1) and
          contain((m: LiveActivityMapping) => m.id must beEqualTo(activeNotLiveNoEvent.id))
        ).await
    }

    "return only mappings that are inactive, not live, and have a last event" in new FetchStatusScope {
      prepareData.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = false, isLive = false, hasLastEvent = true)
      } must (
        haveSize[List[LiveActivityMapping]](1) and
          contain((m: LiveActivityMapping) => m.id must beEqualTo(activeNotLiveWithEvent.id))
        ).await
    }

    "return empty list when no mappings match the given status" in new FetchStatusScope {
      prepareData.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = false, isLive = true, hasLastEvent = true)
      } must beEqualTo(List.empty[LiveActivityMapping]).await
    }

    "return multiple matching mappings" in new FetchStatusScope {
      prepareData.flatMap { _ =>
        repository.fetchAllMappingsByStatus(isChannelActive = true, isLive = false, hasLastEvent = true)
      }.map(_.map(_.id)) must (
        haveSize[List[String]](2) and
          containAllOf(Seq(activeNotLiveWithEvent.id, activeNotLiveWithEvent2.id))
        ).await
    }
  }

  trait FetchStatusScope extends AsyncDynamoScope with ExampleData {
    val repository = new LiveActivityChannelRepository(asyncClient, TableName)

    val activeNotLiveWithEvent   = createFootballMappingWithId("shared-active-notlive-event")
    val activeNotLiveWithEvent2   = createFootballMappingWithId("shared-active-notlive-event2")
    val activeNotLiveNoEvent     = createFootballMappingWithId("shared-active-notlive-noevent")
    val activeLiveWithEvent      = createFootballMappingWithId("shared-active-live-event")
    val inactiveNotLiveWithEvent = createFootballMappingWithId("shared-inactive-notlive-event")

    val prepareData: Future[Unit] = for {
      _ <- repository.createMapping(activeNotLiveWithEvent.id, activeNotLiveWithEvent.channelId, activeNotLiveWithEvent.data)
      _ <- repository.createMapping(activeNotLiveWithEvent2.id, activeNotLiveWithEvent2.channelId, activeNotLiveWithEvent2.data)
      _ <- repository.createMapping(activeNotLiveNoEvent.id, activeNotLiveNoEvent.channelId, activeNotLiveNoEvent.data)
      _ <- repository.createMapping(activeLiveWithEvent.id, activeLiveWithEvent.channelId, activeLiveWithEvent.data)
      _ <- repository.createMapping(inactiveNotLiveWithEvent.id, inactiveNotLiveWithEvent.channelId, inactiveNotLiveWithEvent.data)
      _ <- repository.updateMappingLastEvent(activeNotLiveWithEvent.id, Some("event-1"), Some(ZonedDateTime.now()))
      _ <- repository.updateMappingLastEvent(activeNotLiveWithEvent2.id, Some("event-2"), Some(ZonedDateTime.now()))
      _ <- repository.updateMappingLiveAndLastEvent(activeLiveWithEvent.id, isLive = true, Some("event-3"), Some(ZonedDateTime.now()))
      _ <- repository.updateMappingLastEvent(inactiveNotLiveWithEvent.id, Some("event-4"), Some(ZonedDateTime.now()))
      _ <- repository.updateMappingActiveChannel(inactiveNotLiveWithEvent.id, isActive = false)
    } yield ()
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
