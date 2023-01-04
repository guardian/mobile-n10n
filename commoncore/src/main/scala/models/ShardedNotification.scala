package models

import play.api.libs.json.{Format, Json}

case class ShardRange(start: Short, end: Short) {
  def range: Range = Range.inclusive(start, end)
}

object ShardRange {
  implicit val shardRangeJF: Format[ShardRange] = Json.format[ShardRange]
}

case class ShardedNotification(
  notification: Notification,
  range: ShardRange,
)

object ShardedNotification {
  implicit val shardedNotificationJF: Format[ShardedNotification] = Json.format[ShardedNotification]
}