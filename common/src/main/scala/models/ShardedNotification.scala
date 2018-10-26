package models

case class ShardRange(start: Short, end: Short) {
  def range: Range = Range.inclusive(start, end)
}

case class ShardedNotification(
  notification: Notification,
  range: ShardRange
)
