package models

case class ShardRange(start: Short, end: Short)

case class ShardedNotification(
  notification: Notification,
  range: ShardRange
)
