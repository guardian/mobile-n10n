package models

import java.util.UUID

import com.gu.notifications.events.model.EventAggregation
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

case class ExtendedNotificationReport(
  id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[ExtendedSenderReport],
  events: Option[EventAggregation]
)

object ExtendedSenderReport {
  implicit val jf = Json.format[ExtendedSenderReport]

  def fromSenderReport(s: SenderReport): ExtendedSenderReport = ExtendedSenderReport(
    senderName = s.senderName,
    sentTime = s.sentTime,
    sendersId = s.sendersId,
    platformStatistics = s.platformStatistics
  )
}


object ExtendedNotificationReport {
  implicit val jf = Json.format[ExtendedNotificationReport]

  def fromNotificationReport(r: NotificationReport): ExtendedNotificationReport = ExtendedNotificationReport(
    id = r.id,
    `type` = r.`type`,
    notification = r.notification,
    sentTime = r.sentTime,
    reports = r.reports.map(ExtendedSenderReport.fromSenderReport),
    events = r.events
  )
}

case class ExtendedSenderReport(
  senderName: String,
  sentTime: DateTime,
  sendersId: Option[String] = None,
  platformStatistics: Option[PlatformStatistics] = None
)
