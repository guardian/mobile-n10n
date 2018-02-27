package tracking

import aws.AsyncDynamo
import aws.AsyncDynamo._
import com.amazonaws.services.dynamodbv2.model._
import models.Topic
import play.api.Logger
import tracking.Repository.RepositoryResult

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import utils.LruCache

import scala.collection.JavaConverters._
import scala.util.Failure

class DynamoTopicSubscriptionsRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext) extends TopicSubscriptionsRepository {
  val logger = Logger(classOf[DynamoTopicSubscriptionsRepository])

  object TopicFields {
    val Id = "topicId"
    val Topic = "topic"
    val SubscriberCount = "topicSubscriberCount"
  }

  private val topicCache: LruCache[Option[Topic]] = LruCache[Option[Topic]]()

  override def deviceSubscribed(topic: Topic, count: Int = 1): Future[RepositoryResult[Unit]] = {
    Logger.debug(s"Increasing subscriber count for $topic by $count")
    val subscriptionCountChange = count
    val req = newUpdateRequest
      .addKeyEntry(TopicFields.Id, new AttributeValue(topic.id))
      .withUpdateExpression(s"SET ${TopicFields.Topic} = :topic ADD ${TopicFields.SubscriberCount} :amount")
      .withExpressionAttributeValues(Map(
        ":topic" -> new AttributeValue(topic.toString),
        ":amount" -> new AttributeValue().withN(subscriptionCountChange.toString)
      ).asJava)
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
      ).asJava)
    updateItem(req)
  }

  override def count(topic: Topic): Future[RepositoryResult[Int]] = {
    val q = new QueryRequest(tableName)
      .withKeyConditions(Map(TopicFields.Id -> keyEquals(topic.id)).asJava)
      .withConsistentRead(true)

    client.query(q) map { result =>
      val count = for {
        item <- result.getItems.asScala.headOption
        countField <- Option(item.get(TopicFields.SubscriberCount))
        countFieldValue <- Option(countField.getN)
      } yield countFieldValue.toInt

      Right(count.getOrElse(0))
    }
  }

  override def topicFromId(topicId: String): Future[RepositoryResult[Topic]] = {
    val q = new QueryRequest(tableName)
      .withKeyConditions(Map(TopicFields.Id -> keyEquals(topicId)).asJava)
      .withConsistentRead(true)

    def generate() = client.query(q) map { result =>
      for {
        item <- result.getItems.asScala.headOption
        topicField <- Option(item.get(TopicFields.Topic))
        topicFieldValue <- Option(topicField.getS)
        topic <- Topic.fromString(topicFieldValue).toOption
      } yield topic
    }
    val error: RepositoryResult[Topic] = Left(RepositoryError(s"Topic not found"))
    topicCache.apply(topicId)(generate).map(_.fold(error)(Right.apply))
  }

  private def updateItem(req: UpdateItemRequest) = {
    val eventualUpdate = client.updateItem(req)
    eventualUpdate.failed.foreach {
      case t: Throwable => logger.error(s"DynamoDB communication error ($t)")
    }
    eventualUpdate map { _ => Right(()) }
  }

  private def newUpdateRequest = new UpdateItemRequest().withTableName(tableName)
}
