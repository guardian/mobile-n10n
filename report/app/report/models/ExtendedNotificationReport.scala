package report.models

import java.time.Instant.ofEpochMilli
import java.time.LocalDateTime
import java.time.LocalDateTime.ofInstant
import java.time.ZoneOffset.UTC
import java.util.UUID

import azure.NotificationDetails
import com.gu.notifications.events.model._
import models._
import org.joda.time.DateTime
import play.api.libs.json.{Format, Json}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

case class ExtendedEventAggregation(
  platformCounts: PlatformCount,
  providerCounts: ProviderCount,
  timing: Option[List[(LocalDateTime, Int)]]
)

object ExtendedEventAggregation {
  implicit val extendedEventAggregationJF: Format[ExtendedEventAggregation] = Json.format[ExtendedEventAggregation]

  def full(dynamoEventAggregation: DynamoEventAggregation, originalSentTime: LocalDateTime): ExtendedEventAggregation = {
    def toOrderedTiming(timing: List[List[Int]]): Option[List[(LocalDateTime, Int)]] = {
      val sentTime = originalSentTime.truncatedTo(TenSecondUnit)
      val (_, orderedTiming) = dynamoEventAggregation.timing.map(timed => (timed.head, timed(1))).foldLeft((sentTime, List[(LocalDateTime, Int)]())) {
        case ((lastTime, newList), (offset, count)) =>
          val nextTime = lastTime.plusSeconds(10 * offset)
          (nextTime, newList :+ (nextTime, count))
      }
      if (orderedTiming.isEmpty) None else Some(orderedTiming)
    }

    ExtendedEventAggregation(
      platformCounts = dynamoEventAggregation.platform,
      providerCounts = dynamoEventAggregation.provider,
      timing = toOrderedTiming(dynamoEventAggregation.timing)
    )
  }

  def summary(dynamoEventAggregation: DynamoEventAggregation): ExtendedEventAggregation = {
    ExtendedEventAggregation(
      platformCounts = dynamoEventAggregation.platform,
      providerCounts = dynamoEventAggregation.provider,
      timing = None
    )
  }
}

case class ExtendedSenderReport(
  senderName: String,
  sentTime: DateTime,
  sendersId: Option[String] = None,
  platformStatistics: Option[PlatformStatistics] = None,
  debug: Option[NotificationDetails]
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

case class ExtendedNotificationReport(
  id: UUID,
  `type`: NotificationType,
  notification: Notification,
  sentTime: DateTime,
  reports: List[ExtendedSenderReport],
  events: Option[ExtendedEventAggregation]
)

object ExtendedNotificationReport {
  implicit val jf = Json.format[ExtendedNotificationReport]

  def full(r: DynamoNotificationReport): ExtendedNotificationReport = {
    val javaTimeSentTime = ofInstant(ofEpochMilli(r.sentTime.toInstant.getMillis), UTC)
    ExtendedNotificationReport(
      id = r.id,
      `type` = r.`type`,
      notification = r.notification,
      sentTime = r.sentTime,
      reports = r.reports.map(ExtendedSenderReport.fromSenderReport),
      events = r.events.map(events => ExtendedEventAggregation.full(events, javaTimeSentTime))
    )
  }

  def summary(r: DynamoNotificationReport): ExtendedNotificationReport = ExtendedNotificationReport(
    id = r.id,
    `type` = r.`type`,
    notification = r.notification,
    sentTime = r.sentTime,
    reports = r.reports.map(ExtendedSenderReport.fromSenderReport),
    events = r.events.map(ExtendedEventAggregation.summary)
  )
}
