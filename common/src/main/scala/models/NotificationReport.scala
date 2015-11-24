package models

import java.util.UUID

import org.joda.time.DateTime

import JsonUtils._

case class NotificationReport(
  id: UUID,
  `type`: String,
  notification: Notification,
  sentTime: DateTime,
  statistics: NotificationStatistics
)

object NotificationReport {
  import play.api.libs.json._

  implicit val jf = Json.format[NotificationReport]

  def create(sentTime: DateTime,
    notification: Notification,
    statistics: NotificationStatistics) = NotificationReport(
      id = notification.id,
      `type` = notification.`type`,
      notification = notification,
      sentTime = sentTime,
      statistics = statistics
    )
}
