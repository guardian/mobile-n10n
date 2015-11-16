package report.services

import javax.inject.Inject

import tracking.InMemoryNotificationReportRepository

class NotificationReportRepositorySupport @Inject()() {
  val notificationReportRepository = new InMemoryNotificationReportRepository
}
