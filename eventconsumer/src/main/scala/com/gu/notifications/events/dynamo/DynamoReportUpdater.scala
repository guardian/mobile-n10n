package com.gu.notifications.events.dynamo

import java.time.ZonedDateTime

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import com.gu.notifications.events.model.NotificationReportEvent

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class DynamoReportUpdater(stage: String) {
  private val newEventsKey = ":newevents"
  private val tableName: String = s"mobile-notifications-reports-$stage"

  def updateSetEventsReceivedAfter(eventAggregations: List[NotificationReportEvent], startOfReportingWindow: ZonedDateTime)(implicit executionContext: ExecutionContext, dynamoDbClient: AmazonDynamoDBAsync): List[Future[Unit]] = {
    eventAggregations.map(aggregation => {
      readSentTime(aggregation.id.toString).flatMap {
        case Some(sentTime) if sentTime.isAfter(startOfReportingWindow) => updateSetEvent(aggregation)
        case Some(_) => Future.successful(())
        case None => Future.successful(())
      }
    })
  }

  private def updateSetEvent(notificationReportEvent: NotificationReportEvent)(implicit dynamoDbClient: AmazonDynamoDBAsync): Future[Unit] = {
    val updateItemRequest = new UpdateItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue().withS(notificationReportEvent.id)).asJava)
      .withUpdateExpression(s"SET events = $newEventsKey")
      .withExpressionAttributeValues(Map(
        newEventsKey -> DynamoConversion.toAttributeValue(notificationReportEvent.eventAggregation)
      ).asJava)
    val promise = Promise[Unit]()
    val handler = new AsyncHandler[UpdateItemRequest, UpdateItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(updateItemRequest.toString, exception))

      override def onSuccess(request: UpdateItemRequest, result: UpdateItemResult): Unit = promise.success(())
    }
    dynamoDbClient.updateItemAsync(updateItemRequest, handler)
    promise.future
  }

  private def readSentTime(notificationId: String)(implicit dynamoDbClient: AmazonDynamoDBAsync): Future[Option[ZonedDateTime]] = {
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue("id").withS(notificationId)).asJava)
    val promise = Promise[Option[ZonedDateTime]]()
    val handler = new AsyncHandler[GetItemRequest, GetItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(new Exception(getItemRequest.toString, exception))

      override def onSuccess(request: GetItemRequest, result: GetItemResult): Unit = Try {
        Option(result.getItem).flatMap { item =>
          for {
            sentTime <- if (item.containsKey("sentTime")) Some(item.get("sentTime").getS) else None
            zonedSentTime <- Try(ZonedDateTime.parse(sentTime)).toOption
          } yield zonedSentTime
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
