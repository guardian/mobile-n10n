package tracking

import java.util.UUID

import aws.AsyncDynamo
import aws.AsyncDynamo.{keyBetween, keyEquals}
import aws.DynamoJsonConversions.{fromAttributeMap, toAttributeMap}
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import models.{NotificationReport, NotificationType}
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

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    logger.info(s"Storing: ${report}")
    val putItemRequest = new PutItemRequest(tableName, toAttributeMap(report).asJava)
    (client.putItem(putItemRequest) map { _ => Right(()) }).transform(attempt => {
      logger.info(s"Store attempted: ${report.id}\n $attempt")
      attempt
    })
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
        parsed <- {
          logger.info(s"Read from dynamodb: $item")
          val parsedLocal = Either.fromOption(fromAttributeMap[NotificationReport](item.asScala.toMap).asOpt, RepositoryError("Unable to parse report"))
          logger.info(s"ParsedLocal $parsedLocal")
          parsedLocal
        }
      } yield parsed
    }
  }

  override def update(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    def updateAttempt: Future[RepositoryResult[Unit]] = {
      logger.info(s"Updating: ${report}")
      (for {
        lastResult: RepositoryResult[NotificationReport] <- getByUuid(report.id)
        updated: Either[RepositoryError, Unit] <- lastResult match {
          case Left(error) => Future.successful(Left(error))
          case Right(lastNotificationReport) =>
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
      } yield updated).transform(attempt => {
        logger.info(s"Update attempted: ${report.id}\n $attempt")
        attempt
      })

    }

    def retry(left: Int): Future[RepositoryResult[Unit]] = updateAttempt.transformWith {
      case Success(repositoryResult) => {
        repositoryResult match {
          case Left(_) if left <= 0 => retry(left - 1)
          case _ => Future.successful(repositoryResult)
        }
      }
      case Failure(exception) => if (left <= 0) retry(left - 1) else Future.failed[RepositoryResult[Unit]](exception)
    }

    retry(5)
  }
}
