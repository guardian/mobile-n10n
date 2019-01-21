package com.gu.notifications.events.dynamo

import java.time.ZonedDateTime
import java.util.UUID

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import com.gu.notifications.events.model.{EventAggregation, NotificationReportEvent}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

case class ReadVersionedEvents(version: Option[String], events: Option[EventAggregation])

case class SentTimeAndVersion(sentTime: ZonedDateTime, lastVersion: String)

class DynamoReportUpdater(stage: String) {
  private val newVersionKey = ":newversion"
  private val newEventsKey = ":newevents"
  private val oldVersionKey = ":oldversion"
  private val logger = LogManager.getLogger(classOf[DynamoReportUpdater])
  private val tableName: String = s"mobile-notifications-reports-$stage"

  def updateSetEventsReceivedAfter(eventAggregations: List[NotificationReportEvent], startOfReportingWindow: ZonedDateTime)(implicit executionContext: ExecutionContext, dynamoDbClient: AmazonDynamoDBAsync): List[Future[Unit]] = {
    eventAggregations.map(aggregation => {
      def updateAttempt() = readSentTime(aggregation.id.toString).flatMap(_.map(sentTimeAndVersion => {
        if (sentTimeAndVersion.sentTime.isAfter(startOfReportingWindow)) {
          updateSetEvent(sentTimeAndVersion.lastVersion, aggregation)
        }
        else {
          logger.info(s"skipping ${aggregation.id}")
          Future.successful(())
        }
      }).getOrElse(Future.successful(())))
      def retryUpdate(retriesLeft: Int): Future[Unit] = updateAttempt().transformWith {
        case Success(value) => Future.successful(value)
        case Failure(t) => if (retriesLeft == 0) Future.failed[Unit](t) else {
          logger.warn(s"Retry failed for $aggregation", t)
          retryUpdate(retriesLeft - 1)
        }
      }
      retryUpdate(5)
    })
  }

  private def updateSetEvent(lastVersion: String, notificationReportEvent: NotificationReportEvent)(implicit dynamoDbClient: AmazonDynamoDBAsync): Future[Unit] = {
    val updateItemRequest = new UpdateItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue().withS(notificationReportEvent.id)).asJava)
      .withUpdateExpression(s"SET events = $newEventsKey, version = $newVersionKey")
      .withConditionExpression(s"version = $oldVersionKey")
      .withExpressionAttributeValues(Map(
        newEventsKey -> DynamoConversion.toAttributeValue(notificationReportEvent.eventAggregation),
        newVersionKey -> new AttributeValue().withS(nextVersion()),
        oldVersionKey -> new AttributeValue().withS(lastVersion)
      ).asJava)
    val promise = Promise[Unit]
    val handler = new AsyncHandler[UpdateItemRequest, UpdateItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(updateItemRequest.toString, exception))

      override def onSuccess(request: UpdateItemRequest, result: UpdateItemResult): Unit = promise.success(())
    }
    dynamoDbClient.updateItemAsync(updateItemRequest, handler)
    promise.future
  }


  private def nextVersion() = UUID.randomUUID().toString

  private def readSentTime(notificationId: String)(implicit dynamoDbClient: AmazonDynamoDBAsync): Future[Option[SentTimeAndVersion]] = {
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue("id").withS(notificationId)).asJava)
    val promise = Promise[Option[SentTimeAndVersion]]
    val handler = new AsyncHandler[GetItemRequest, GetItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(getItemRequest.toString, exception))

      override def onSuccess(request: GetItemRequest, result: GetItemResult): Unit = Try {
        Option(result.getItem).flatMap { item =>
          for {
            sentTime <- if (item.containsKey("sentTime")) Some(item.get("sentTime").getS) else None
            version <- if (item.containsKey("version")) Some(item.get("sentTime").getS) else None
            zonedSentTime <- Try(ZonedDateTime.parse(sentTime)).toOption
          } yield SentTimeAndVersion(zonedSentTime, version)
        }
      } match {
        case Success(value) => promise.success(value)
        case Failure(exception) => promise.failure(new Exception(request.toString, exception))
      }
    }
    dynamoDbClient.getItemAsync(getItemRequest, handler)
    promise.future
  }

}
