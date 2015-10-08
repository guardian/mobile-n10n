package tracking

import aws.AsyncDynamo
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model._
import models.TopicTypes.TagKeyword
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.ShouldMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import org.specs2.specification.Scope

import scala.collection.JavaConversions._

class DynamoTopicSubscriptionsRepositorySpec(implicit ev: ExecutionEnv) extends Specification with Mockito with ShouldMatchers with BeforeAfterAll {

  sequential

  override def beforeAll() = createTable()
  override def afterAll() = destroyTable()

  "A DynamoTopicSubscriptionsRepository" should {
    "increment a subscriptions counter" in new RepositoryScope {
      val topic = Topic(TagKeyword, "test-topic-1")
      val result = for {
        initialCount <- repository.count(topic)
        _ <- repository.subscribe(topic)
        endCount <- repository.count(topic)
      } yield (initialCount.toOption.get, endCount.toOption.get)

      result must beEqualTo((0, 1)).await
    }

    "decrement a subscriptions counter" in new RepositoryScope {
      val topic = Topic(TagKeyword, "test-topic-2")
      val result = for {
        initialCount <- repository.count(topic)
        _ <- repository.subscribe(topic)
        _ <- repository.subscribe(topic)
        middleCount <- repository.count(topic)
        _ <- repository.unsubscribe(topic)
        endCount <- repository.count(topic)
      } yield (initialCount.toOption.get, middleCount.toOption.get, endCount.toOption.get)

      result must beEqualTo((0, 2, 1)).await
    }

    "increment topics independently" in new RepositoryScope {
      val topicA = Topic(TagKeyword, "test-topic-a")
      val topicB = Topic(TagKeyword, "test-topic-b")

      val result = for {
        initialCountA <- repository.count(topicA)
        initialCountB <- repository.count(topicB)
        _ <- repository.subscribe(topicA)
        _ <- repository.subscribe(topicA)
        _ <- repository.subscribe(topicA)
        _ <- repository.subscribe(topicA)
        _ <- repository.subscribe(topicA)
        _ <- repository.subscribe(topicB)
        _ <- repository.subscribe(topicB)
        endCountA <- repository.count(topicA)
        endCountB <- repository.count(topicB)
      } yield List(initialCountA, initialCountB, endCountA, endCountB).map(_.toOption.get)

      result must beEqualTo(List(0, 0, 5, 2)).await
    }
  }

  private val TableName = "test-topic-table"
  private val TestEndpoint = "http://localhost:8000"
  private val TopicIdField = "topicId"

  trait RepositoryScope extends Scope {
    val asyncClient = {
      val client = new AmazonDynamoDBAsyncClient(new DefaultAWSCredentialsProviderChain())
      client.setEndpoint(TestEndpoint)
      new AsyncDynamo(client)
    }

    val repository = new DynamoTopicSubscriptionsRepository(asyncClient, TableName)
  }

  def createTable() = {
    val awsClient = new AmazonDynamoDBAsyncClient(new DefaultAWSCredentialsProviderChain())
    awsClient.setEndpoint(TestEndpoint)

    val req = new CreateTableRequest(TableName, List(new KeySchemaElement(TopicIdField, KeyType.HASH)))
      .withAttributeDefinitions(List(new AttributeDefinition(TopicIdField, ScalarAttributeType.S)))
      .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))

    awsClient.createTable(req)
  }

  def destroyTable() = {
    val awsClient = new AmazonDynamoDBAsyncClient(new DefaultAWSCredentialsProviderChain())
    awsClient.setEndpoint(TestEndpoint)
    awsClient.deleteTable(new DeleteTableRequest(TableName))
  }
}
