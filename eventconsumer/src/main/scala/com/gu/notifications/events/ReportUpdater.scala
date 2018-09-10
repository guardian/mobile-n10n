package com.gu.notifications.events

import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.model._
import com.gu.notifications.events.model.{EventAggregation, NotificationReportEvent}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

case class ReadVersionedEvents(version: Option[String], events: Option[EventAggregation], sentTime: LocalDateTime)

case class UpdateVersionedEvents(lastVersion: Option[String], nextVersion: String, events: NotificationReportEvent)

object ReportUpdater {
  lazy val reportUpdater = new ReportUpdater
}

class ReportUpdater {
  private val newVersionKey = ":newversion"
  private val newEventsKey = ":newevents"
  private val oldVersionKey = ":oldversion"
  val logger = LogManager.getLogger(classOf[ReportUpdater])
  val tableName: String = "mobile-notifications-reports-CODE"

  def apply(eventAggregations: List[NotificationReportEvent])(implicit executionContext: ExecutionContext): List[Future[Unit]] = {
    eventAggregations.map(aggregation => {
      def nextVersion = UUID.randomUUID().toString

      def updateAttempt() = for {
        previousEvents <- read(aggregation.id.toString)
        nextEvents = previousEvents.events.map(previous => aggregation.copy(eventAggregation = EventAggregation.combine(previous, aggregation.eventAggregation))).getOrElse(aggregation)
        updatedEvents = UpdateVersionedEvents(previousEvents.version, nextVersion, nextEvents)
        updatedEventsSuccess <- update(updatedEvents, previousEvents.sentTime)
      } yield updatedEventsSuccess

      def retryUpdate(retriesLeft: Int): Future[Unit] = updateAttempt().transformWith {
        case Success(value) => Future.successful(value)
        case Failure(t) => if (retriesLeft == 0) Future.failed[Unit](t) else {
          logger.warn(t)
          retryUpdate(retriesLeft - 1)
        }
      }

      retryUpdate(5)
    })
  }

  private def update(versionedEvents: UpdateVersionedEvents, sentTime: LocalDateTime): Future[Unit] = {
    val promise = Promise[Unit]
    val attributeValueUpdates = Map(
      newEventsKey -> DynamoConversion.toAttributeValue(versionedEvents.events.eventAggregation, sentTime),
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
          .withExpressionAttributeValues((attributeValueUpdates ++ Map(oldVersionKey -> new AttributeValue().withS(version))).asJava)
      )
      .getOrElse(updateItemRequestWithoutCondition.withExpressionAttributeValues(attributeValueUpdates.asJava))
    logger.info("Updating {}", updateItemRequest)
    val handler = new AsyncHandler[UpdateItemRequest, UpdateItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(updateItemRequest.toString, exception))

      override def onSuccess(request: UpdateItemRequest, result: UpdateItemResult): Unit = promise.success(())
    }
    AwsClient.dynamoDbClient.updateItemAsync(updateItemRequestWithoutCondition, handler)
    promise.future
  }

  private def read(notificationId: String): Future[ReadVersionedEvents] = {
    val promise = Promise[ReadVersionedEvents]
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue("id").withS(notificationId)).asJava)
    logger.info("Getting {}", getItemRequest)
    val handler = new AsyncHandler[GetItemRequest, GetItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(getItemRequest.toString, exception))

      override def onSuccess(request: GetItemRequest, result: GetItemResult): Unit = Try {
        logger.info("Got {}", result)
        val item = result.getItem
        val version = if (item.containsKey("version")) Some(item.get("version").getS) else None
        val sentTime = ZonedDateTime.parse(item.get("sentTime").getS).toLocalDateTime
        val events: Option[EventAggregation] = if (item.containsKey("events")) {
          Some(DynamoConversion.fromAttributeValue(item.get("events"), notificationId, sentTime))
        }
        else {
          None
        }

        ReadVersionedEvents(version, events, sentTime)
      }.fold(t => promise.failure(new Exception(request.toString, t)), promise.success)
    }
    AwsClient.dynamoDbClient.getItemAsync(getItemRequest, handler)
    promise.future
  }
}
