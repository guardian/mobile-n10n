package com.gu.liveactivities

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import software.amazon.awssdk.services.dynamodb.model._
import scala.jdk.CollectionConverters._
import com.gu.liveactivities.service.LiveActivityChannelRepository
import com.gu.liveactivities.models.FootballLiveActivity
import com.gu.liveactivities.models.LiveActivityMapping
import com.gu.liveactivities.models.LiveActivityData
import com.gu.liveactivities.service.ChannelMappingsRepository
import java.time.ZonedDateTime
import com.gu.liveactivities.models.RepositoryException
import com.gu.liveactivities.models.LiveActivityInvalidStateException


class LiveActivityChannelRepositoryTest(implicit ev: ExecutionEnv)
    extends DynamodbSpecification
    with Mockito {

  override val TableName = "test-table"

  "LiveActivityChannelRepository" should {

    "save a new channel mapping for an id if it does not exist" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0001")
      repository.createMapping(
        footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data, 
        footballMapping.competitionId).flatMap { _ =>
        repository.getMappingById(footballMapping.id)
      } must beEqualTo(footballMapping).await
    }

    "Error if trying to save a new channel mapping for an id that already exists" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0004")
      repository.createMapping(footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data, 
        footballMapping.competitionId).flatMap { _ =>
        repository.createMapping(footballMapping.id, 
          footballMapping.channelId, 
          footballMapping.data, 
          footballMapping.competitionId)
      } must throwA[RepositoryException].await
    }

    "delete a channel mapping for an activity id if it exists" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0002")
      repository.createMapping(
        footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data, 
        footballMapping.competitionId).flatMap { _ =>
        repository.deleteMappingById(footballMapping.id)
      } must beEqualTo(()).await
    }

    "get a channel mapping for an activity id if it exists" in new RepositoryScope
      with ExampleData {
      val footballMapping = createFootballMappingWithId("football-0003")
      repository.createMapping(
        footballMapping.id, 
        footballMapping.channelId, 
        footballMapping.data, 
        footballMapping.competitionId).flatMap { _ =>
        repository.getMappingById(footballMapping.id)
      } must beEqualTo(footballMapping).await
    }
  }

  trait RepositoryScope extends AsyncDynamoScope {
    val repository = new LiveActivityChannelRepository(asyncClient, TableName)
  }

  trait ExampleData {
    val footballData = FootballLiveActivity(
      homeTeam = "HomeTeamName",
      awayTeam = "AwayTeamName",
      articleUrl = "https://www.theguardian.com/football/test-article-id"
    )

    val footballMappingTemplate = LiveActivityMapping(
      id = "football-1234567",
      channelId = "test-channel-id",
      isChannelActive = true,
      isEventLive = true,     
      data = Some(footballData),
      competitionId = Some("test-competition-id"),
      lastEventId = None,
      lastEventAt = None,
    )

    def createFootballMappingWithId(id: String): LiveActivityMapping = {
      footballMappingTemplate.copy(id = id)
    }
  }

  override def createTableRequest: CreateTableRequest = {
    val IdField = "id"

    val attrs: List[AttributeDefinition] = List(
      AttributeDefinition
        .builder()
        .attributeName(IdField)
        .attributeType(ScalarAttributeType.S)
        .build(),
    )
    val keySchema: List[KeySchemaElement] = List(
      KeySchemaElement.builder().attributeName(IdField).keyType(KeyType.HASH).build(),
    )

    CreateTableRequest
      .builder()
      .tableName(TableName)
      .keySchema(keySchema.asJava)
      .attributeDefinitions(attrs.asJava)
      .provisionedThroughput(
        ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build(),
      )
      .build();
  }

}
