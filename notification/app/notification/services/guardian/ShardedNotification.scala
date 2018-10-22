package notification.services.guardian

import models.Notification

case class ShardRange(start: Short, end: Short)

case class ShardedNotification(
  notification: Notification,
  range: ShardRange
)
