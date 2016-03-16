package tracking

import aws.AsyncDynamo
import aws.AsyncDynamo._
import com.amazonaws.services.dynamodbv2.model._
import models.Topic
import play.api.Logger
import tracking.Repository.RepositoryResult

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

import scala.collection.JavaConversions._

class DynamoTopicSubscriptionsRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext) extends TopicSubscriptionsRepository {
  val logger = Logger(classOf[DynamoTopicSubscriptionsRepository])
  val TopicIdField = "topicId"
  val TopicSubscriberCountField = "topicSubscriberCount"

  override def deviceSubscribed(topic: Topic): Future[RepositoryResult[Unit]] =
    updateCount(topic, 1)

  override def deviceUnsubscribed(topic: Topic): Future[RepositoryResult[Unit]] =
    updateCount(topic, -1)

  override def count(topic: Topic): Future[RepositoryResult[Int]] = {
    val q = new QueryRequest(tableName)
      .withKeyConditions(Map(TopicIdField -> keyEquals(topic.toString)))
      .withConsistentRead(true)

    client.query(q) map { result =>
      val count = for {
        item <- result.getItems.headOption
        countField <- Option(item.get(TopicSubscriberCountField))
        countFieldValue <- Option(countField.getN)
      } yield countFieldValue.toInt

      \/.right(count getOrElse 0)
    }
  }

  private def updateCount(topic: Topic, amount: Int) = {
    val valueUpdate = new AttributeValueUpdate()
      .withValue(new AttributeValue().withN(amount.toString))
      .withAction(AttributeAction.ADD)

    val req = new UpdateItemRequest()
      .withTableName(tableName)
      .addKeyEntry(TopicIdField, new AttributeValue(topic.toString))
      .addAttributeUpdatesEntry(TopicSubscriberCountField, valueUpdate)

    val eventualUpdate = client.updateItem(req)
    eventualUpdate.onFailure {
      case t: Throwable => logger.error(s"DynamoDB communication error ($t)")
    }
    eventualUpdate map { _ => \/.right(()) }
  }

}
