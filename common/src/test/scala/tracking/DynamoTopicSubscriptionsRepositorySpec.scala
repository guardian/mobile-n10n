package tracking

import com.amazonaws.services.dynamodbv2.model._
import models.TopicTypes.TagKeyword
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import scala.concurrent.duration._

import scala.collection.JavaConversions._

class DynamoTopicSubscriptionsRepositorySpec(implicit ev: ExecutionEnv) extends DynamodbSpecification with Mockito {

  override val TableName = "test-topic-table"

  "A DynamoTopicSubscriptionsRepository" should {
    "increment a subscriptions counter" in new RepositoryScope {
      val topic = Topic(TagKeyword, "test-topic-1")
      val result = for {
        initialCount <- repository.count(topic)
        _ <- repository.deviceSubscribed(topic)
        endCount <- repository.count(topic)
      } yield (initialCount.toOption.get, endCount.toOption.get)

      result must beEqualTo((0, 1)).awaitFor(5 seconds)
    }

    "decrement a subscriptions counter" in new RepositoryScope {
      val topic = Topic(TagKeyword, "test-topic-2")
      val result = for {
        initialCount <- repository.count(topic)
        _ <- repository.deviceSubscribed(topic)
        _ <- repository.deviceSubscribed(topic)
        middleCount <- repository.count(topic)
        _ <- repository.deviceUnsubscribed(topic.id)
        endCount <- repository.count(topic)
      } yield (initialCount.toOption.get, middleCount.toOption.get, endCount.toOption.get)

      result must beEqualTo((0, 2, 1)).awaitFor(5 seconds)
    }

    "increment topics independently" in new RepositoryScope {
      val topicA = Topic(TagKeyword, "test-topic-a")
      val topicB = Topic(TagKeyword, "test-topic-b")

      val result = for {
        initialCountA <- repository.count(topicA)
        initialCountB <- repository.count(topicB)
        _ <- repository.deviceSubscribed(topicA)
        _ <- repository.deviceSubscribed(topicA)
        _ <- repository.deviceSubscribed(topicA)
        _ <- repository.deviceSubscribed(topicA)
        _ <- repository.deviceSubscribed(topicA)
        _ <- repository.deviceSubscribed(topicB)
        _ <- repository.deviceSubscribed(topicB)
        endCountA <- repository.count(topicA)
        endCountB <- repository.count(topicB)
      } yield List(initialCountA, initialCountB, endCountA, endCountB).map(_.toOption.get)

      result must beEqualTo(List(0, 0, 5, 2)).awaitFor(5 seconds)
    }
  }

  trait RepositoryScope extends AsyncDynamoScope {
    val repository = new DynamoTopicSubscriptionsRepository(asyncClient, TableName)
  }

  def createTableRequest = {
    val TopicIdField = "topicId"

    new CreateTableRequest(TableName, List(new KeySchemaElement(TopicIdField, KeyType.HASH)))
      .withAttributeDefinitions(List(new AttributeDefinition(TopicIdField, ScalarAttributeType.S)))
      .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
  }
}
