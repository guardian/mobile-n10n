package tracking

import aws.AsyncDynamo
import aws.AsyncDynamo._
import com.amazonaws.services.dynamodbv2.model._
import models.Topic
import play.api.Logger
import tracking.Repository.RepositoryResult

import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import cats.implicits._

import scala.collection.JavaConversions._

class DynamoTopicSubscriptionsRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext) extends TopicSubscriptionsRepository {
  val logger = Logger(classOf[DynamoTopicSubscriptionsRepository])

  object TopicFields {
    val Id = "topicId"
    val Topic = "topic"
    val SubscriberCount = "topicSubscriberCount"
  }

  override def deviceSubscribed(topic: Topic, count: Int = 1): Future[RepositoryResult[Unit]] = {
    Logger.debug(s"Increasing subscriber count for $topic by $count")
    val subscriptionCountChange = count
    val req = newUpdateRequest
      .addKeyEntry(TopicFields.Id, new AttributeValue(topic.id))
      .withUpdateExpression(s"SET ${TopicFields.Topic} = :topic ADD ${TopicFields.SubscriberCount} :amount")
      .withExpressionAttributeValues(Map(
        ":topic" -> new AttributeValue(topic.toString),
        ":amount" -> new AttributeValue().withN(subscriptionCountChange.toString)
      ))
    updateItem(req)
  }

  override def deviceUnsubscribed(topicId: String, count: Int = 1): Future[RepositoryResult[Unit]] = {
    Logger.debug(s"Reducing subscriber count for $topicId by $count")
    val subscriptionCountChange = -count
    val req = newUpdateRequest
      .addKeyEntry(TopicFields.Id, new AttributeValue(topicId))
      .withUpdateExpression(s"ADD ${TopicFields.SubscriberCount} :amount")
      .withExpressionAttributeValues(Map(
        ":amount" -> new AttributeValue().withN(subscriptionCountChange.toString)
      ))
    updateItem(req)
  }

  override def count(topic: Topic): Future[RepositoryResult[Int]] = {
    val q = new QueryRequest(tableName)
      .withKeyConditions(Map(TopicFields.Id -> keyEquals(topic.id)))
      .withConsistentRead(true)

    client.query(q) map { result =>
      val count = for {
        item <- result.getItems.headOption
        countField <- Option(item.get(TopicFields.SubscriberCount))
        countFieldValue <- Option(countField.getN)
      } yield countFieldValue.toInt

      count.getOrElse(0).right
    }
  }

  private def updateItem(req: UpdateItemRequest) = {
    val eventualUpdate = client.updateItem(req)
    eventualUpdate.onFailure {
      case t: Throwable => logger.error(s"DynamoDB communication error ($t)")
    }
    eventualUpdate map { _ => ().right }
  }

  private def newUpdateRequest = new UpdateItemRequest().withTableName(tableName)
}
