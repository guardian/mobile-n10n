package models

import play.api.libs.json.{Format, Json}

import java.time.Instant

case class NotificationMetadata(notificationAppReceivedTime: Instant, audienceSize: Option[Int])

object NotificationMetadata {
  implicit val notificationMetadataJF: Format[NotificationMetadata] = Json.format[NotificationMetadata]
}