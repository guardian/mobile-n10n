package tracking

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._

import scalaz.\/
import scalaz.std.option.optionSyntax._

import org.joda.time.DateTime

import com.amazonaws.services.dynamodbv2.model._

import aws.AsyncDynamo
import aws.AsyncDynamo._
import aws.DynamoJsonConversions._

import models.NotificationReport
import tracking.Repository.RepositoryResult


class DynamoNotificationReportRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext)
  extends SentNotificationReportRepository {

  private val SentTimeField = "sentTime"
  private val UuidField = "uuid"
  private val TypeField = "type"
  private val SentTimeIndex = "sentTime-index"

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    val putItemRequest = new PutItemRequest(tableName, toAttributeMap(report))
    client.putItem(putItemRequest) map { _ => \/.right(()) }
  }

  override def getByTypeWithDateRange(notificationType: String, from: DateTime, to: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    val q = new QueryRequest(tableName)
      .withIndexName(SentTimeIndex)
      .withKeyConditions(Map(
        TypeField -> keyEquals(notificationType),
        SentTimeField -> keyBetween(from.toString, to.toString)
      ))

    client.query(q) map { result =>
      \/.right(result.getItems.toList.flatMap { item =>
        fromAttributeMap[NotificationReport](item.toMap).asOpt
      })
    }
  }

  override def getByUuid(uuid: String): Future[RepositoryResult[NotificationReport]] = {
    val q = new QueryRequest(tableName)
      .withKeyConditions(Map(UuidField -> keyEquals(uuid)))
      .withConsistentRead(true)

    client.query(q) map { result =>
      for {
        item <- result.getItems.headOption \/> RepositoryError("UUID not found")
        parsed <- fromAttributeMap[NotificationReport](item.toMap).asOpt \/> RepositoryError("Unable to parse report")
      } yield parsed
    }
  }
}