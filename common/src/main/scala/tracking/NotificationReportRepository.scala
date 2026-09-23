package tracking

import java.util
import java.util.UUID

import aws.AsyncDynamo
import aws.AsyncDynamo.{keyBetween, keyEquals}
import aws.DynamoJsonConversions.{fromAttributeMap, toAttributeMap}
import cats.syntax.all._
import software.amazon.awssdk.services.dynamodb.model._
import models.{NotificationReport, NotificationType}
import org.joda.time.{DateTime, Days}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsSuccess}
import tracking.Repository.RepositoryResult

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

class NotificationReportRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext)
  extends SentNotificationReportRepository {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val SentTimeField = "sentTime"
  private val IdField = "id"
  private val TypeField = "type"
  private val SentTimeIndex = "sentTime-index"

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    val putItemRequest = PutItemRequest.builder()
      .tableName(tableName)
      .item(toAttributeMap(report).asJava)
      .build()
    client.putItem(putItemRequest) map { _ => Right(()) }
  }

  override def getByTypeWithDateRange(notificationType: NotificationType, from: DateTime, to: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    if (Days.daysBetween(from, to).getDays > 31) {
      return Future.successful(Left(RepositoryError("Date range too big to query")))
    }

    def maybeStartKey(result: QueryResponse): Option[util.Map[String, AttributeValue]] =
      if (result.hasLastEvaluatedKey && !result.lastEvaluatedKey.isEmpty) Some(result.lastEvaluatedKey) else None

    def reportsFromResult(result: QueryResponse): RepositoryResult[List[NotificationReport]] = {
      val results = result.items.asScala.toList.map { item =>
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

    def buildDynamoQuery(startKey: Option[util.Map[String, AttributeValue]]): QueryRequest = QueryRequest.builder()
      .tableName(tableName)
      .indexName(SentTimeIndex)
      .keyConditions(Map(
        TypeField -> keyEquals(notificationType.value),
        SentTimeField -> keyBetween(from.toString, to.toString)
      ).asJava)
      .exclusiveStartKey(startKey.orNull)
      .build()

    def fetch(
      startKey: Option[util.Map[String, AttributeValue]] = None,
      lastListResult: RepositoryResult[List[NotificationReport]] = Right(Nil)
    ): Future[RepositoryResult[List[NotificationReport]]] = {
      client.query(buildDynamoQuery(startKey)) flatMap { result =>
        val reports = for {
          lastList <- lastListResult
          fetched <- reportsFromResult(result)
        } yield lastList ++ fetched
        maybeStartKey(result) match {
          case None => Future.successful(reports)
          case Some(newStartKey) => fetch(Some(newStartKey), reports)
        }
      }
    }

    fetch()
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[NotificationReport]] = {
    val getItemRequest = GetItemRequest.builder()
      .tableName(tableName)
      .key(Map(IdField -> AttributeValue.builder().s(uuid.toString).build()).asJava)
      .consistentRead(true)
      .build()

    client.get(getItemRequest) map { result =>
      for {
        item <- Either.fromOption(if (result.hasItem) Some(result.item) else None, RepositoryError("UUID not found"))
        parsed <- Either.fromOption(fromAttributeMap[NotificationReport](item.asScala.toMap).asOpt, RepositoryError("Unable to parse report"))
      } yield parsed
    }
  }

  override def update(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    val updateItemRequest = UpdateItemRequest.builder()
      .key(Map("id" -> AttributeValue.builder().s(report.id.toString).build()).asJava)
      .tableName(tableName)
      .attributeUpdates(
        toAttributeMap(report)
          .filterNot { case (key, _) => key == "id" }
          .view.mapValues(value => AttributeValueUpdate.builder().action(AttributeAction.PUT).value(value).build()).toMap.asJava
      )
      .build()
    client.updateItem(updateItemRequest).map { _ => Right(()) }
  }
}
