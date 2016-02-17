package tracking

import java.util.UUID
import models.{NotificationType, NotificationReport}
import org.joda.time.{DateTime, Interval}
import tracking.Repository.RepositoryResult
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.\/
import scalaz.std.option.optionSyntax._
import scalaz.syntax.either._

class InMemoryNotificationReportRepository extends SentNotificationReportRepository with TrackingObserver {

  val db = scala.collection.mutable.MutableList.empty[NotificationReport]

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    db += report
    Future.successful(().right)
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[NotificationReport]] = {
    Future.successful(db.find(_.id == uuid) \/> RepositoryError("Notification report not found"))
  }

  override def getByTypeWithDateRange(notificationType: NotificationType, from: DateTime, until: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    val interval = new Interval(from, until)
    Future.successful(\/.right(db.filter({report =>
      report.`type` == notificationType && (interval contains report.sentTime)
    }).toList))
  }

  override def notificationSent(report: NotificationReport): Future[TrackerResult[Unit]] = store(report).map {
    case e => e.leftMap { e => RepositoryTrackingError(e.message) }
  }
}

case class RepositoryTrackingError(message: String) extends TrackingError {
  override def reason = s"Could not store notification report in repository. ($message)"
}
