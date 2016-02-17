import error.NotificationsError
import models.NotificationReport

import scala.concurrent.Future
import scalaz.\/

package object tracking {
  trait TrackingError extends NotificationsError

  type TrackerResult[T] = TrackingError \/ T

  trait TrackingObserver {
    def notificationSent(notificationReport: NotificationReport): Future[TrackerResult[Unit]]
  }
}
