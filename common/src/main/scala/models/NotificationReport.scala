package models

import org.joda.time.DateTime

case class NotificationReport(sentTime: DateTime, notification: Notification, statistics: NotificationStatistics)
