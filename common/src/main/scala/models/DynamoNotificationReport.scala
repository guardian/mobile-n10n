package models

import java.util.UUID

import JsonUtils._
import com.github.nscala_time.time.Imports._
import com.gu.notifications.events.model.{DynamoEventAggregation, EventAggregation}
import models.NotificationType.BreakingNews
import org.joda.time.DateTime
import play.api.libs.json.Json

case class DynamoNotificationReport(
  id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[SenderReport],
  version: Option[UUID],
  events: Option[DynamoEventAggregation],
  ttl: Option[Long]
)

case class NotificationReport(id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[SenderReport],
  version: Option[UUID],
  events: Option[EventAggregation],
  ttl: Option[Long]
) {
  def this(dynamoNotificationReport: DynamoNotificationReport) = this(
    id = dynamoNotificationReport.id,
    `type` = dynamoNotificationReport.`type`,
    notification = dynamoNotificationReport.notification,
    sentTime = dynamoNotificationReport.sentTime,
    reports = dynamoNotificationReport.reports,
    version = dynamoNotificationReport.version,
    events = dynamoNotificationReport.events.map(dea => EventAggregation.from(dea)),
    ttl = dynamoNotificationReport.ttl
  )
}


case class SenderReport(
  senderName: String,
  sentTime: DateTime,
  sendersId: Option[String] = None,
  platformStatistics: Option[PlatformStatistics] = None
)

object SenderReport {
  implicit val jf = Json.format[SenderReport]
}

object DynamoNotificationReport {
  def create(
    id: UUID,
    `type`: NotificationType,
    notification: Notification,
    sentTime: DateTime,
    reports: List[SenderReport],
    version: Option[UUID],
    events: Option[DynamoEventAggregation]
  ) = DynamoNotificationReport(
    id,
    `type`,
    notification,
    sentTime,
    reports,
    version,
    events,
    ttlFromSentTime(sentTime, notification)
  )

  private def ttlFromSentTime(sentTime: DateTime, notification: Notification): Option[Long] = {
    notification.`type` match {
      case BreakingNews if notification.dryRun.contains(true) => Some(sentTime.plusDays(7).getMillis / 1000)
      case BreakingNews => None
      case _ => Some(sentTime.plusMonths(3).getMillis / 1000)

    }
  }

  def create(notification: Notification, reports: List[SenderReport], version: Option[UUID]): DynamoNotificationReport = {
    require(reports.nonEmpty)
    val lastSentTime = reports.map {
      _.sentTime
    }.sorted.last
    DynamoNotificationReport(
      id = notification.id,
      `type` = notification.`type`,
      notification = notification,
      sentTime = lastSentTime,
      reports,
      version,
      None,
      ttlFromSentTime(lastSentTime, notification)
    )
  }

  implicit val jf = Json.format[DynamoNotificationReport]
}
object NotificationReport {

  implicit val jf = Json.format[NotificationReport]
}