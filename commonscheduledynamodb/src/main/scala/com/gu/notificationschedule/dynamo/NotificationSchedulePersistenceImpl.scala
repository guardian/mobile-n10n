package com.gu.notificationschedule.dynamo

import java.time.Instant

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._
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

class NotificationSchedulePersistenceImpl(tableName: String, client: DynamoDbAsyncClient) extends NotificationSchedulePersistenceSync with NotificationSchedulePersistenceAsync {

  private val due_and_sent = "due_epoch_s_and_sent"

  def querySync(): Seq[NotificationsScheduleEntry] = {
    val request = ScanRequest.builder()
      .tableName(tableName)
      .indexName(due_and_sent)
      .filterExpression("sent = :sent and due_epoch_s < :now")
      .expressionAttributeValues(Map(
        ":sent" -> AttributeValue.builder().s(false.toString).build(),
        ":now" -> AttributeValue.builder().n(Instant.now().getEpochSecond.toString).build()
      ).asJava)
      .build()
    client.scan(request).join().items().asScala.toList.map(item => NotificationsScheduleEntry(
      uuid = item.get("uuid").s,
      notification = item.get("notification").s,
      dueEpochSeconds = item.get("due_epoch_s").n.toLong,
      ttlEpochSeconds = item.get("ttl_epoch_s").n.toLong
    ))
  }

  private def makePutItemRequest(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): PutItemRequest = PutItemRequest.builder()
    .tableName(tableName)
    .item((Map(
      "uuid" -> AttributeValue.builder().s(notificationsScheduleEntry.uuid).build(),
      "notification" -> AttributeValue.builder().s(notificationsScheduleEntry.notification).build(),
      "due_epoch_s" -> AttributeValue.builder().n(notificationsScheduleEntry.dueEpochSeconds.toString).build(),
      "ttl_epoch_s" -> AttributeValue.builder().n(notificationsScheduleEntry.ttlEpochSeconds.toString).build(),
      "sent" -> AttributeValue.builder().s(maybeEpochSentS.isDefined.toString).build()
    ) ++ maybeEpochSentS.map(epochSentS => Map("sent_epoch_s" -> AttributeValue.builder().n(epochSentS.toString).build())).getOrElse(Map[String, AttributeValue]())).asJava)
    .build()


  def writeAsync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): Promise[Unit] = {
    val request = makePutItemRequest(notificationsScheduleEntry, maybeEpochSentS)
    val promise = Promise[Unit]()
    client.putItem(request).whenComplete { (_, exception) =>
      if (exception != null) promise.failure(exception) else promise.success(())
      ()
    }
    promise
  }

  override def writeSync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): Unit = {
    client.putItem(makePutItemRequest(notificationsScheduleEntry, maybeEpochSentS)).join()
    ()
  }

}
