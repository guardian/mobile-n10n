package models

import org.joda.time.DateTime

import JsonUtils._

case class NotificationReport(
  uuid: String,
  `type`: String,
  sender: String,
  timeToLiveInSeconds: Int,
  payload: MessagePayload,
  sentTime: DateTime,
  statistics: NotificationStatistics
)

object NotificationReport {
  import play.api.libs.json._

  implicit val jf = Json.format[NotificationReport]

  def create(sentTime: DateTime, notification: Notification, statistics: NotificationStatistics): NotificationReport = {
    NotificationReport(
      notification.uuid,
      notification.payload.`type`.getOrElse("default"),
      notification.sender,
      notification.timeToLiveInSeconds,
      notification.payload,
      sentTime,
      statistics
    )
  }
}
