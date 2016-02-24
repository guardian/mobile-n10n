package notification.services

import javax.inject.Inject

import tracking.{TrackingObserver, InMemoryNotificationReportRepository}

class NotificationReportRepositorySupport @Inject()() {
  val notificationReportRepository: TrackingObserver = new InMemoryNotificationReportRepository
}
