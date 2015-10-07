package models

import org.joda.time.DateTime

import JsonUtils._

case class NotificationReport(sentTime: DateTime, notification: Notification, statistics: NotificationStatistics)

object NotificationReport {
  import play.api.libs.json._

  implicit val jf = Json.format[NotificationReport]
}
