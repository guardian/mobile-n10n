package notification.services

import javax.inject.Inject

import tracking.{SentNotificationReportRepository, InMemoryNotificationReportRepository}

class NotificationReportRepositorySupport @Inject()() {
  val notificationReportRepository: SentNotificationReportRepository = new InMemoryNotificationReportRepository
}
