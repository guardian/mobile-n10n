package models

import java.time.Instant.ofEpochMilli
import java.time.LocalDateTime.ofInstant
import java.time.ZoneOffset.UTC
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

import azure.NotificationDetails
import com.gu.notifications.events.model.{DynamoEventAggregation, EventAggregation}
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
    platformStatistics = s.platformStatistics,
    debug = None
  )
}


object ExtendedNotificationReport {
  implicit val jf = Json.format[ExtendedNotificationReport]

  def fromNotificationReport(r: DynamoNotificationReport): ExtendedNotificationReport = ExtendedNotificationReport(
    id = r.id,
    `type` = r.`type`,
    notification = r.notification,
    sentTime = r.sentTime,
    reports = r.reports.map(ExtendedSenderReport.fromSenderReport),
    events = r.events.map(EventAggregation.from(_, ofInstant(ofEpochMilli(r.sentTime.toInstant.getMillis), UTC)))
  )
}

case class ExtendedSenderReport(
  senderName: String,
  sentTime: DateTime,
  sendersId: Option[String] = None,
  platformStatistics: Option[PlatformStatistics] = None,
  debug: Option[NotificationDetails]
)
