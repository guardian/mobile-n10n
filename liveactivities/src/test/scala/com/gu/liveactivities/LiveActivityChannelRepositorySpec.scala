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
      isLive = true,     
      data = Some(footballData),
      lastEventId = None,
      lastEventAt = None,
      createdAt = ZonedDateTime.now(),
      lastModifiedAt = ZonedDateTime.now()
    )

    def createFootballMappingWithId(id: String): LiveActivityMapping = {
      footballMappingTemplate.copy(id = id)
    }
  }

  override def targetTableAttributes: Seq[(String, ScalarAttributeType)] = Seq(
    "id" -> S
  )
}
