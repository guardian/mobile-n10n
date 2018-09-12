package tracking

import java.util.UUID

import aws.AsyncDynamo
import aws.AsyncDynamo.{keyBetween, keyEquals}
import aws.DynamoJsonConversions.{fromAttributeMap, toAttributeMap}
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import models.{DynamoNotificationReport, NotificationType}
import org.joda.time.DateTime
import play.api.Logger
import tracking.Repository.RepositoryResult

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DynamoNotificationReportRepository(client: AsyncDynamo, tableName: String)
  (implicit ec: ExecutionContext)
  extends SentNotificationReportRepository {
  val logger = Logger(classOf[DynamoNotificationReportRepository])
  private val SentTimeField = "sentTime"
  private val IdField = "id"
  private val TypeField = "type"
  private val SentTimeIndex = "sentTime-index"

  override def store(report: DynamoNotificationReport): Future[RepositoryResult[Unit]] = {
    val putItemRequest = new PutItemRequest(tableName, toAttributeMap(report).asJava)
    client.putItem(putItemRequest) map { _ => Right(()) }
  }

  override def getByTypeWithDateRange(notificationType: NotificationType, from: DateTime, to: DateTime): Future[RepositoryResult[List[DynamoNotificationReport]]] = {
    val q = new QueryRequest(tableName)
      .withIndexName(SentTimeIndex)
      .withKeyConditions(Map(
        TypeField -> keyEquals(notificationType.value),
        SentTimeField -> keyBetween(from.toString, to.toString)
      ).asJava)

    client.query(q) map { result =>
      Right(result.getItems.asScala.toList.flatMap { item =>
        fromAttributeMap[DynamoNotificationReport](item.asScala.toMap).asOpt
      })
    }
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[DynamoNotificationReport]] = {
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withKey(Map(IdField -> new AttributeValue().withS(uuid.toString)).asJava)
      .withConsistentRead(true)

    client.get(getItemRequest) map { result =>
      for {
        item <- Either.fromOption(Option(result.getItem), RepositoryError("UUID not found"))
        parsed <- Either.fromOption(fromAttributeMap[DynamoNotificationReport](item.asScala.toMap).asOpt, RepositoryError("Unable to parse report"))
      } yield parsed
    }
  }

  override def update(report: DynamoNotificationReport): Future[RepositoryResult[Unit]] = {
    def updateAttempt: Future[RepositoryResult[Unit]] = {
      for {
        lastResult: RepositoryResult[DynamoNotificationReport] <- getByUuid(report.id)
        updateResult: Either[RepositoryError, Unit] <- lastResult match {
          case Left(error) => Future.successful(Left(error))
          case Right(lastNotificationReport) => updateNotificationReport(report, lastNotificationReport)
        }
      } yield updateResult
    }

    def retry(left: Int): Future[RepositoryResult[Unit]] = updateAttempt.transformWith {
      case Success(repositoryResult) => {
        repositoryResult match {
          case Left(error) if left <= 0 => {
            logger.warn(s"Retry of report: $report\nError: $error")
            retry(left - 1)
          }
          case _ => Future.successful(repositoryResult)
        }
      }
      case Failure(exception) => {
        if (left <= 0) {
          logger.warn(s"Retry error $report", exception)
          retry(left - 1)
        } else Future.failed[RepositoryResult[Unit]](exception)
      }
    }

    retry(20)
  }

  private def updateNotificationReport(report: DynamoNotificationReport, lastNotificationReport: DynamoNotificationReport): Future[Right[Nothing, Unit]] = {
    val updateItemRequest = new UpdateItemRequest()
      .withKey(Map("id" -> new AttributeValue().withS(report.id.toString)).asJava)
      .withTableName(tableName)
      .withAttributeUpdates(
        toAttributeMap(report)
          .filterNot { case (key, _) => key == "id" }
          .mapValues(value => new AttributeValueUpdate().withAction(AttributeAction.PUT).withValue(value)).asJava)
      .withExpected(Map("version" -> new ExpectedAttributeValue().withValue(new AttributeValue().withS(lastNotificationReport.version.get.toString)).withComparisonOperator(ComparisonOperator.EQ)).asJava)
    client.updateItem(updateItemRequest).map { _ => Right(()) }
  }
}
