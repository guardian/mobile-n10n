package com.gu.notifications.events.dynamo

import java.time.ZonedDateTime
import java.util.UUID

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.model._
import com.gu.notifications.events.aws.AwsClient
import com.gu.notifications.events.model.{EventAggregation, NotificationReportEvent}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

case class ReadVersionedEvents(version: Option[String], events: Option[EventAggregation])

case class UpdateVersionedEvents(lastVersion: Option[String], nextVersion: String, events: NotificationReportEvent)

class DynamoReportUpdater(stage: String) {
  private val newVersionKey = ":newversion"
  private val newEventsKey = ":newevents"
  private val oldVersionKey = ":oldversion"
  private val logger = LogManager.getLogger(classOf[DynamoReportUpdater])
  private val tableName: String = s"mobile-notifications-reports-$stage"

  def update(eventAggregations: List[NotificationReportEvent])(implicit executionContext: ExecutionContext): List[Future[Unit]] = {
    eventAggregations.map(aggregation => {

      def updateAttempt() = {
        read(aggregation.id.toString).flatMap(_.map(updateFromPreviousEvents(aggregation, _)).getOrElse(Future.successful(())))
      }

      def retryUpdate(retriesLeft: Int): Future[Unit] = updateAttempt().transformWith {
        case Success(value) => Future.successful(value)
        case Failure(t) => if (retriesLeft == 0) Future.failed[Unit](t) else {
          logger.warn(s"Retry failed for $aggregation", t)
          retryUpdate(retriesLeft - 1)
        }
      }

      retryUpdate(20)
    })
  }

  def updateSetEventsReceivedAfter(eventAggregations: List[NotificationReportEvent], startOfReportingWindow: ZonedDateTime)(implicit executionContext: ExecutionContext): List[Future[Unit]] = {
    eventAggregations.map(aggregation => {
      def updateAttempt() = readSentTime(aggregation.id.toString).flatMap(_.map(sentTime => {
        if (sentTime.isAfter(startOfReportingWindow)) {
          updateSetEvent(aggregation)
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

      retryUpdate(20)
    })
  }

  private def updateSetEvent(notificationReportEvent: NotificationReportEvent): Future[Unit] = {
    val updateItemRequest = new UpdateItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue().withS(notificationReportEvent.id)).asJava)
      .withUpdateExpression(s"SET events = $newEventsKey, version = $newVersionKey").withExpressionAttributeValues(Map(
      newEventsKey -> DynamoConversion.toAttributeValue(notificationReportEvent.eventAggregation),
      newVersionKey -> new AttributeValue().withS(nextVersion())
    ).asJava)
    val promise = Promise[Unit]
    val handler = new AsyncHandler[UpdateItemRequest, UpdateItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(updateItemRequest.toString, exception))

      override def onSuccess(request: UpdateItemRequest, result: UpdateItemResult): Unit = promise.success(())
    }
    AwsClient.dynamoDbClient.updateItemAsync(updateItemRequest, handler)
    promise.future
  }

  private def updateFromPreviousEvents(aggregation: NotificationReportEvent, previousEvents: ReadVersionedEvents): Future[Unit] = {
    val nextEvents = previousEvents.events.map(previous => aggregation.copy(eventAggregation = EventAggregation.combine(previous, aggregation.eventAggregation))).getOrElse(aggregation)
    val updatedEvents = UpdateVersionedEvents(previousEvents.version, nextVersion(), nextEvents)
    update(updatedEvents)
  }

  private def nextVersion() = UUID.randomUUID().toString

  private def update(versionedEvents: UpdateVersionedEvents): Future[Unit] = {
    val attributeValuesForUpdate = Map(
      newEventsKey -> DynamoConversion.toAttributeValue(versionedEvents.events.eventAggregation),
      newVersionKey -> new AttributeValue().withS(versionedEvents.nextVersion)
    )
    val updateItemRequestWithoutCondition = new UpdateItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue().withS(versionedEvents.events.id)).asJava)
      .withUpdateExpression(s"SET events = $newEventsKey, version = $newVersionKey")
    val updateItemRequest = versionedEvents
      .lastVersion
      .map(version => updateItemRequestWithoutCondition
        .withConditionExpression(s"version = $oldVersionKey")
        .withExpressionAttributeValues((attributeValuesForUpdate ++ Map(oldVersionKey -> new AttributeValue().withS(version))).asJava)
      )
      .getOrElse(updateItemRequestWithoutCondition.withExpressionAttributeValues(attributeValuesForUpdate.asJava))

    val promise = Promise[Unit]
    val handler = new AsyncHandler[UpdateItemRequest, UpdateItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(updateItemRequest.toString, exception))

      override def onSuccess(request: UpdateItemRequest, result: UpdateItemResult): Unit = promise.success(())
    }
    AwsClient.dynamoDbClient.updateItemAsync(updateItemRequest, handler)
    promise.future
  }

  private def readSentTime(notificationId: String): Future[Option[ZonedDateTime]] = {
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue("id").withS(notificationId)).asJava)
    val promise = Promise[Option[ZonedDateTime]]
    val handler = new AsyncHandler[GetItemRequest, GetItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(getItemRequest.toString, exception))

      override def onSuccess(request: GetItemRequest, result: GetItemResult): Unit = Try {
        Option(result.getItem).flatMap { item =>
          if (item.containsKey("sentTime")) Some(item.get("sentTime").getS) else None
        }.flatMap(sentTime => Try(ZonedDateTime.parse(sentTime)).toOption)
      } match {
        case Success(value) => promise.success(value)
        case Failure(exception) => promise.failure(new Exception(request.toString, exception))
      }
    }
    AwsClient.dynamoDbClient.getItemAsync(getItemRequest, handler)
    promise.future
  }

  private def read(notificationId: String): Future[Option[ReadVersionedEvents]] = {
    val promise = Promise[Option[ReadVersionedEvents]]
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withConsistentRead(true)
      .withKey(Map("id" -> new AttributeValue("id").withS(notificationId)).asJava)
    val handler = new AsyncHandler[GetItemRequest, GetItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(getItemRequest.toString, exception))

      override def onSuccess(request: GetItemRequest, result: GetItemResult): Unit = Try {
        Option(result.getItem).map { item =>
          ReadVersionedEvents(
            version = if (item.containsKey("version")) Some(item.get("version").getS) else None,
            events = if (item.containsKey("events")) {
              Some(DynamoConversion.fromAttributeValue(item.get("events")))
            }
            else {
              None
            })
        }
      } match {
        case Failure(exception) => promise.failure(new Exception(request.toString, exception))
        case Success(value) => promise.success(value)
      }
    }
    AwsClient.dynamoDbClient.getItemAsync(getItemRequest, handler)
    promise.future
  }
}
