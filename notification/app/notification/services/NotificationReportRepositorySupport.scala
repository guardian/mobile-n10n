package notification.services

import javax.inject.Inject

import tracking.InMemoryNotificationReportRepository

final class NotificationReportRepositorySupport @Inject()() {
  val notificationReportRepository = new InMemoryNotificationReportRepository
}
