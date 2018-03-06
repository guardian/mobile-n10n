package tracking

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

import cats.implicits._

import org.joda.time.DateTime

import com.amazonaws.services.dynamodbv2.model._

import aws.AsyncDynamo
import aws.AsyncDynamo._
import aws.DynamoJsonConversions._

import models.{NotificationReport, NotificationType}
import tracking.Repository.RepositoryResult


class DynamoNotificationReportRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext)
  extends SentNotificationReportRepository {

  private val SentTimeField = "sentTime"
  private val IdField = "id"
  private val TypeField = "type"
  private val SentTimeIndex = "sentTime-index"

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    val putItemRequest = new PutItemRequest(tableName, toAttributeMap(report).asJava)
    client.putItem(putItemRequest) map { _ => Right(()) }
  }

  override def getByTypeWithDateRange(notificationType: NotificationType, from: DateTime, to: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    val q = new QueryRequest(tableName)
      .withIndexName(SentTimeIndex)
      .withKeyConditions(Map(
        TypeField -> keyEquals(notificationType.value),
        SentTimeField -> keyBetween(from.toString, to.toString)
      ).asJava)

    client.query(q) map { result =>
      Right(result.getItems.asScala.toList.flatMap { item =>
        fromAttributeMap[NotificationReport](item.asScala.toMap).asOpt
      })
    }
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[NotificationReport]] = {
    val q = new QueryRequest(tableName)
      .withKeyConditions(Map(IdField -> keyEquals(uuid.toString)).asJava)
      .withConsistentRead(true)

    client.query(q) map { result =>
      for {
        item <- Either.fromOption(result.getItems.asScala.headOption, RepositoryError("UUID not found"))
        parsed <- Either.fromOption(fromAttributeMap[NotificationReport](item.asScala.toMap).asOpt, RepositoryError("Unable to parse report"))
      } yield parsed
    }
  }
}
