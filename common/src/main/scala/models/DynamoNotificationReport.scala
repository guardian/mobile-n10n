package models

import java.util.UUID

import JsonUtils._
import com.github.nscala_time.time.Imports._
import com.gu.notifications.events.model.{DynamoEventAggregation, EventAggregation}
import org.joda.time.DateTime
import play.api.libs.json.Json

case class DynamoNotificationReport(
  id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[SenderReport],
  version: Option[UUID],
  events: Option[DynamoEventAggregation]
)

case class NotificationReport(id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[SenderReport],
  version: Option[UUID],
  events: Option[EventAggregation]
) {
  def this(dynamoNotificationReport: DynamoNotificationReport) = this(
    id = dynamoNotificationReport.id,
    `type` = dynamoNotificationReport.`type`,
    notification = dynamoNotificationReport.notification,
    sentTime = dynamoNotificationReport.sentTime,
    reports = dynamoNotificationReport.reports,
    version = dynamoNotificationReport.version,
    events = dynamoNotificationReport.events.map(dea => EventAggregation.from(dea))
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
  def create(notification: Notification, reports: List[SenderReport], version: Option[UUID]): DynamoNotificationReport = {
    require(reports.nonEmpty)
    DynamoNotificationReport(
      id = notification.id,
      `type` = notification.`type`,
      notification = notification,
      sentTime = reports.map {
        _.sentTime
      }.sorted.last,
      reports,
      version,
      None
    )
  }

  implicit val jf = Json.format[DynamoNotificationReport]
}
object NotificationReport {

  implicit val jf = Json.format[NotificationReport]
}