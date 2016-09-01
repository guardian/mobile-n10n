package tracking

import java.util.UUID
import models.{NotificationReport, NotificationType}
import org.joda.time.{DateTime, Interval}
import tracking.Repository.RepositoryResult

import scala.concurrent.Future
import cats.data.Xor
import cats.implicits._

class InMemoryNotificationReportRepository extends SentNotificationReportRepository {

  val db = scala.collection.mutable.MutableList.empty[NotificationReport]

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    db += report
    Future.successful(().right)
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[NotificationReport]] = {
    Future.successful(Xor.fromOption(db.find(_.id == uuid), RepositoryError("Notification report not found")))
  }

  override def getByTypeWithDateRange(`type`: NotificationType, from: DateTime, until: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    val interval = new Interval(from, until)
    Future.successful(db.filter({report =>
      report.`type` == `type` && (interval contains report.sentTime)
    }).toList.right)
  }
}
