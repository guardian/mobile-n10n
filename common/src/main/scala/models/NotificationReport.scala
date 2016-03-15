package models

import java.util.UUID

import org.joda.time.DateTime
import JsonUtils._
import play.api.libs.json.Json
import com.github.nscala_time.time.Imports._

case class NotificationReport(
  id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[SenderReport]
)

case class SenderReport(
  senderName: String,
  sentTime: DateTime,
  platformStatistics: Option[PlatformStatistics] = None
)

object SenderReport {
  implicit val jf = Json.format[SenderReport]
}

object NotificationReport {
  def create(notification: Notification, reports: List[SenderReport]): NotificationReport = {
    require(reports.nonEmpty)
    NotificationReport(
      id = notification.id,
      `type` = notification.`type`,
      notification = notification,
      sentTime = reports.map { _.sentTime }.sorted.last,
      reports
    )
  }

  import play.api.libs.json._

  implicit val jf = Json.format[NotificationReport]
}
