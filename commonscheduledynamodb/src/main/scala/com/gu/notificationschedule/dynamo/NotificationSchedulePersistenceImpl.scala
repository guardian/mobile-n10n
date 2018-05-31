package com.gu.notificationschedule.dynamo

import java.time.Instant

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._

import scala.collection.JavaConverters._
import scala.concurrent.Promise


case class ScheduleTableConfig(app: String, stage: String, stack: String) {
  val scheduleTableName: String = s"$app-$stage-$stack"
}

case class NotificationsScheduleEntry(
                                       uuid: String,
                                       notification: String,
                                       dueEpochSeconds: Long,
                                       ttlEpochSeconds: Long
                                     ) {
}

trait NotificationSchedulePersistenceSync {
  def querySync(): Seq[NotificationsScheduleEntry]
  def writeSync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): Unit
}

trait NotificationSchedulePersistenceAsync {
  def writeAsync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): Promise[Unit]
}

class NotificationSchedulePersistenceImpl(tableName: String, client: AmazonDynamoDBAsync) extends NotificationSchedulePersistenceSync with NotificationSchedulePersistenceAsync {

  private val due_and_sent = "due_epoch_s_and_sent"

  def querySync(): Seq[NotificationsScheduleEntry] = client.scan(new ScanRequest(tableName)
    .withIndexName(due_and_sent)
    .withFilterExpression("sent = :sent and due_epoch_s < :now")
    .withExpressionAttributeValues(Map(
      ":sent" -> new AttributeValue().withS(false.toString),
      ":now" -> new AttributeValue().withN(Instant.now().getEpochSecond.toString)
    ).asJava)).getItems.asScala.map(item => NotificationsScheduleEntry(
    uuid = item.get("uuid").getS,
    notification = item.get("notification").getS,
    dueEpochSeconds = item.get("due_epoch_s").getN.toLong,
    ttlEpochSeconds = item.get("ttl_epoch_s").getN.toLong
  )
  )

  private def makePutItemRequest(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]) = new PutItemRequest(tableName, (Map(
    "uuid" -> new AttributeValue().withS(notificationsScheduleEntry.uuid),
    "notification" -> new AttributeValue().withS(notificationsScheduleEntry.notification),
    "due_epoch_s" -> new AttributeValue().withN(notificationsScheduleEntry.dueEpochSeconds.toString),
    "ttl_epoch_s" -> new AttributeValue().withN(notificationsScheduleEntry.ttlEpochSeconds.toString),
    "sent" -> new AttributeValue().withS(maybeEpochSentS.isDefined.toString)
  ) ++ maybeEpochSentS.map(epochSentS => Map("sent_epoch_s" -> new AttributeValue().withN(epochSentS.toString))).getOrElse(Map[String, AttributeValue]())).asJava
  )


  def writeAsync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): Promise[Unit] = {
    val request = makePutItemRequest(notificationsScheduleEntry, maybeEpochSentS)
    val promise = Promise[Unit]
    client.putItemAsync(request, new AsyncHandler[PutItemRequest, PutItemResult] {
      override def onError(exception: Exception): Unit = promise.failure(exception)

      override def onSuccess(request: PutItemRequest, result: PutItemResult): Unit = promise.success(())
    })
    promise
  }

  override def writeSync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): Unit =
    client.putItem(makePutItemRequest(notificationsScheduleEntry, maybeEpochSentS))

}
