package models

import java.util.UUID
import JsonUtils._
import com.github.nscala_time.time.Imports._
import com.gu.notifications.events.model.DynamoEventAggregation
import org.joda.time.DateTime
import play.api.libs.json.Json

case class NotificationReport(
  id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[SenderReport],
  version: Option[UUID],
  events: Option[DynamoEventAggregation]
)

case class SenderReport(
  senderName: String,
  sentTime: DateTime,
  sendersId: Option[String] = None,
  platformStatistics: Option[PlatformStatistics] = None
)

object SenderReport {
  implicit val jf = Json.format[SenderReport]
}

object NotificationReport {
  def create(notification: Notification, reports: List[SenderReport], version: Option[UUID]): NotificationReport = {
    require(reports.nonEmpty)
    NotificationReport(
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

  implicit val jf = Json.format[NotificationReport]
}
