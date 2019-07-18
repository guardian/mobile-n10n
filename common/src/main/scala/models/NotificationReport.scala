package models

import java.util.UUID

import JsonUtils._
import com.github.nscala_time.time.Imports._
import com.gu.notifications.events.model.EventAggregation
import models.NotificationType.BreakingNews
import org.joda.time.DateTime
import play.api.libs.json.Json

case class NotificationReport(id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[SenderReport],
  events: Option[EventAggregation],
  ttl: Option[Long]
)

case class SenderReport(
  senderName: String,
  sentTime: DateTime,
  sendersId: Option[String] = None,
  platformStatistics: Option[PlatformStatistics] = None,
  totalRegistrationCount: Option[Int] = None
)

object SenderReport {
  implicit val jf = Json.format[SenderReport]
}

object NotificationReport {
  def create(
    id: UUID,
    `type`: NotificationType,
    notification: Notification,
    sentTime: DateTime,
    reports: List[SenderReport],
    events: Option[EventAggregation]
  ) = NotificationReport(
    id,
    `type`,
    notification,
    sentTime,
    reports,
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

  def create(notification: Notification, reports: List[SenderReport]): NotificationReport = {
    require(reports.nonEmpty)
    val lastSentTime = reports.map {
      _.sentTime
    }.sorted.last
    NotificationReport(
      id = notification.id,
      `type` = notification.`type`,
      notification = notification,
      sentTime = lastSentTime,
      reports,
      None,
      ttlFromSentTime(lastSentTime, notification)
    )
  }

  implicit val jf = Json.format[NotificationReport]
}