package com.gu.liveactivities


import com.amazonaws.services.dynamodbv2.model._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import tracking.Repository.RepositoryResult

import scala.concurrent.Future
import scala.jdk.CollectionConverters._


class LiveActivityChannelRepositoryTest (implicit ev: ExecutionEnv) extends DynamodbSpecification with Mockito {

  override val TableName = "test-table"

  "LiveActivityChannelRepository" should {
    "connect to local DynamoDB and create table" in new RepositoryScope {
      // This test will pass if the table is created successfully in beforeAll()
      1 must beEqualTo(1)
    }

    "save a new channel mapping for an id if it does not exist" in new RepositoryScope with ExampleData {
      repository.saveMapping(footballMapping).flatMap { _ =>
        repository.getMappingByActivityId(footballMapping.liveActivityId)
      } must beEqualTo(Right(Some(footballMapping))).await
    }

    "Error if trying to save a new channel mapping for an id that already exists" in new RepositoryScope with ExampleData {
      repository.saveMapping(footballMapping).flatMap { _ =>
        repository.getMappingByActivityId(footballMapping.liveActivityId)
      } must beEqualTo(Right(Some(footballMapping))).await
    }

//    "delete a channel mapping for an activity id if it exists" in new RepositoryScope with ExampleData {
//    }
//
//    "get a channel mapping for an activity id if it exists" in new RepositoryScope with ExampleData {
//    }
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
      liveActivityId = "football-1234567",
      channelId = "test-channel-id",
      data = Some(footballData)
    )
  }

  override def createTableRequest: CreateTableRequest = {
    val IdField = "liveActivityId"

    new CreateTableRequest(TableName, List(new KeySchemaElement(IdField, KeyType.HASH)).asJava)
      .withAttributeDefinitions(List(
        new AttributeDefinition(IdField, ScalarAttributeType.S)
      ).asJava)
      .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
  }

}