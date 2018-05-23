package com.gu.notificationschedule.dynamo

import java.time.Instant
import java.util

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest, ScanRequest}
import com.gu.notificationschedule.NotificationScheduleConfig

import scala.collection.JavaConverters._


case class NotificationsScheduleEntry(
                                       uuid: String,
                                       notification: String,
                                       due_epoch_s: Long,
                                       ttl_epoch_s: Long
                                     ) {
}

trait NotificationSchedulePersistence {
  def query(): Seq[NotificationsScheduleEntry]
  def write(notificationsScheduleEntry: NotificationsScheduleEntry, sent: Boolean, sent_epoch_s: Long): Unit
}

class NotificationSchedulePersistenceImpl(config: NotificationScheduleConfig, client: AmazonDynamoDBAsync) extends NotificationSchedulePersistence {
  private val due_and_sent = "due_epoch_s_and_sent"

  def query(): Seq[NotificationsScheduleEntry] = {
    val scanRequest = new ScanRequest(config.notificationScheduleTable)
      .withIndexName(due_and_sent)
      .withFilterExpression("sent = :sent and due_epoch_s < :now")
      .withExpressionAttributeValues(Map(
        ":sent" -> new AttributeValue().withS(false.toString),
        ":now" -> new AttributeValue().withN(Instant.now().getEpochSecond.toString)
      ).asJava)
    val scanResult = client.scan(scanRequest)
    val items: util.List[util.Map[String, AttributeValue]] = scanResult.getItems
    items.asScala.map(item => NotificationsScheduleEntry(
      uuid = item.get("uuid").getS,
      notification = item.get("notification").getS,
      due_epoch_s = item.get("due_epoch_s").getN.toLong,
      ttl_epoch_s = item.get("ttl_epoch_s").getN.toLong
    )
    )
  }

  def write(notificationsScheduleEntry: NotificationsScheduleEntry, sent: Boolean, sent_epoch_s: Long): Unit = {
    client.putItem(new PutItemRequest(config.notificationScheduleTable, Map(
      "uuid" -> new AttributeValue().withS(notificationsScheduleEntry.uuid),
      "notification" -> new AttributeValue().withS(notificationsScheduleEntry.notification),
      "due_epoch_s" -> new AttributeValue().withN(notificationsScheduleEntry.due_epoch_s.toString),
      "ttl_epoch_s" -> new AttributeValue().withN(notificationsScheduleEntry.ttl_epoch_s.toString),
      "sent" -> new AttributeValue().withS(sent.toString),
      "sent_epoch_s" -> new AttributeValue().withN(sent_epoch_s.toString)
    ).asJava
    ))
  }


}
