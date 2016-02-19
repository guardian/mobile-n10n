import error.NotificationsError
import models.NotificationReport

import scala.concurrent.Future
import scalaz.\/

package object tracking {
  trait TrackingError extends NotificationsError {
    def description: String
    final override def reason: String = s"Tracking error. $description"
  }

  type TrackerResult[T] = TrackingError \/ T

  trait TrackingObserver {
    def notificationSent(notificationReport: NotificationReport): Future[TrackerResult[Unit]]
  }
}
