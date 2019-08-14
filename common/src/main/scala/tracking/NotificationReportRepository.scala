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
import play.api.libs.json.{JsError, JsSuccess}
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

    def maybeStartKey(result: QueryResult): Option[util.Map[String, AttributeValue]] = Option(result.getLastEvaluatedKey).flatMap(lastKey => if (lastKey.isEmpty) None else Some(lastKey))

    def reportsFromResult(result: QueryResult): RepositoryResult[List[NotificationReport]] = {
      val results = result.getItems.asScala.toList.map { item =>
        fromAttributeMap[NotificationReport](item.asScala.toMap)
      }
      val error = results.collectFirst {
        case JsError(errors) => Left(RepositoryError(s"Unable to parse notification report $errors"))
      }

      val reports = results.collect{
        case JsSuccess(report, _) => report
      }

      error.getOrElse(Right(reports))
    }

    def buildDynamoQuery(startKey: Option[util.Map[String, AttributeValue]]): QueryRequest = new QueryRequest(tableName)
      .withIndexName(SentTimeIndex)
      .withKeyConditions(Map(
        TypeField -> keyEquals(notificationType.value),
        SentTimeField -> keyBetween(from.toString, to.toString)
      ).asJava)
      .withExclusiveStartKey(startKey.orNull)

    def fetch(
      startKey: Option[util.Map[String, AttributeValue]] = None,
      lastList: RepositoryResult[List[NotificationReport]] = Right(Nil)
    ): Future[RepositoryResult[List[NotificationReport]]] = {
      client.query(buildDynamoQuery(startKey)) flatMap { result =>
        val reports = for {
          list1 <- lastList
          list2 <- reportsFromResult(result)
        } yield list1 ++ list2
        maybeStartKey(result) match {
          case None => Future.successful(reports)
          case Some(newStartKey) => fetch(Some(newStartKey), reports)
        }
      }
    }

    fetch()
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
