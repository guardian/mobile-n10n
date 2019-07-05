package tracking

import java.util
import java.util.UUID

import aws.AsyncDynamo
import aws.AsyncDynamo.{keyBetween, keyEquals}
import aws.DynamoJsonConversions.{fromAttributeMap, toAttributeMap}
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import models.{NotificationReport, NotificationType}
import org.joda.time.{DateTime, Days}
import play.api.Logger
import tracking.Repository.RepositoryResult

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class NotificationReportRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext)
  extends SentNotificationReportRepository {
  val logger = Logger(classOf[NotificationReportRepository])
  private val SentTimeField = "sentTime"
  private val IdField = "id"
  private val TypeField = "type"
  private val SentTimeIndex = "sentTime-index"

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    val putItemRequest = new PutItemRequest(tableName, toAttributeMap(report).asJava)
    client.putItem(putItemRequest) map { _ => Right(()) }
  }

  override def getByTypeWithDateRange(notificationType: NotificationType, from: DateTime, to: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    if (Days.daysBetween(from, to).getDays > 31) {
      return Future.successful(Left(RepositoryError("Date range too big to query")))
    }

    def maybeLastKey(result: QueryResult): Option[util.Map[String, AttributeValue]] = Option(result.getLastEvaluatedKey).flatMap(lastKey => if (lastKey.isEmpty) None else Some(lastKey))

    def reportsFromResult(result: QueryResult): List[NotificationReport] = result.getItems.asScala.toList.flatMap { item =>
      fromAttributeMap[NotificationReport](item.asScala.toMap).asOpt
    }

    def query(): QueryRequest = new QueryRequest(tableName)
      .withIndexName(SentTimeIndex)
      .withKeyConditions(Map(
        TypeField -> keyEquals(notificationType.value),
        SentTimeField -> keyBetween(from.toString, to.toString)
      ).asJava)

    def queryWithKey(lastKey: util.Map[String, AttributeValue]): QueryRequest = query().withExclusiveStartKey(lastKey)

    def enrichWithRest(lastKey: util.Map[String, AttributeValue], lastList: List[NotificationReport]): Future[List[NotificationReport]] = client.query(queryWithKey(lastKey)) flatMap { result =>
      val reports = lastList ++ reportsFromResult(result)
      maybeLastKey(result).map(enrichWithRest(_, reports)).getOrElse(Future.successful(reports))
    }

    (client.query(query()) flatMap ((result: QueryResult) => {
      val reports = reportsFromResult(result)
      maybeLastKey(result).map(enrichWithRest(_, reports)).getOrElse(Future.successful(reports))
    })).map(Right(_))
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[NotificationReport]] = {
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withKey(Map(IdField -> new AttributeValue().withS(uuid.toString)).asJava)
      .withConsistentRead(true)

    client.get(getItemRequest) map { result =>
      for {
        item <- Either.fromOption(Option(result.getItem), RepositoryError("UUID not found"))
        parsed <- Either.fromOption(fromAttributeMap[NotificationReport](item.asScala.toMap).asOpt, RepositoryError("Unable to parse report"))
      } yield parsed
    }
  }

  override def update(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    val updateItemRequest = new UpdateItemRequest()
      .withKey(Map("id" -> new AttributeValue().withS(report.id.toString)).asJava)
      .withTableName(tableName)
      .withAttributeUpdates(
        toAttributeMap(report)
          .filterNot { case (key, _) => key == "id" }
          .mapValues(value => new AttributeValueUpdate().withAction(AttributeAction.PUT).withValue(value)).asJava)
    client.updateItem(updateItemRequest).map { _ => Right(()) }
  }
}
