package com.gu.liveactivities

import com.amazonaws.services.dynamodbv2.model._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import tracking.Repository.RepositoryResult
import tracking.RepositoryError
import scala.jdk.CollectionConverters._
import com.gu.liveactivities.service.LiveActivityChannelRepository
import com.gu.liveactivities.models.FootballLiveActivity
import com.gu.liveactivities.models.LiveActivityMapping
import com.gu.liveactivities.models.LiveActivityData
import java.time.ZonedDateTime
import com.gu.liveactivities.service.ChannelMappingsRepository


class LiveActivityChannelRepositoryTest(implicit ev: ExecutionEnv)
    extends DynamodbSpecification
    with Mockito {

  override val TableName = "test-table"

  "LiveActivityChannelRepository" should {
    "connect to local DynamoDB and create table" in new RepositoryScope {
      // This test will pass if the table is created successfully in beforeAll()
      1 must beEqualTo(1)
    }

    "save a new channel mapping for an id if it does not exist" in new RepositoryScope
      with ExampleData {
      repository.createMapping(footballMapping).flatMap { _ =>
        repository.getMappingById(footballMapping.id)
      } must beEqualTo(Right(footballMapping)).await
    }

    // "Error if trying to save a new channel mapping for an id that already exists" in new RepositoryScope
    //   with ExampleData {
    //   repository.createMapping(footballMapping).flatMap { _ =>
    //     repository.createMapping(footballMapping)
    //   } must beLike[ChannelMappingsRepository.Result[Unit]] {
    //     case Left(RepositoryException(msg, _))
    //         if msg.contains("ConditionalCheckFailed") =>
    //       ok
    //   }.await
    // }

    "delete a channel mapping for an activity id if it exists" in new RepositoryScope
      with ExampleData {
      repository.createMapping(footballMapping).flatMap { _ =>
        repository.deleteMappingById(footballMapping.id)
      } must beEqualTo(Right(())).await
    }

    "get a channel mapping for an activity id if it exists" in new RepositoryScope
      with ExampleData {
      repository.createMapping(footballMapping).flatMap { _ =>
        repository.getMappingById(footballMapping.id)
      } must beEqualTo(Right(footballMapping)).await
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

    val footballMapping = LiveActivityMapping(
      id = "football-1234567",
      channelId = "test-channel-id",
      isChannelActive = true,
      isEventLive = true,     
      eventData = Some(footballData),
      competitionId = Some("test-competition-id"),
      lastEventId = None,
      lastEventUpdate = None,
    )
  }

  override def createTableRequest: CreateTableRequest = {
    val IdField = "id"

    new CreateTableRequest(
      TableName,
      List(new KeySchemaElement(IdField, KeyType.HASH)).asJava
    )
      .withAttributeDefinitions(
        List(
          new AttributeDefinition(IdField, ScalarAttributeType.S)
        ).asJava
      )
      .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
  }

}
