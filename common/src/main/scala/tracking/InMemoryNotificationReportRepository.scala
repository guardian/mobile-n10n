package tracking

import models.NotificationReport
import org.joda.time.DateTime
import tracking.Repository.RepositoryResult

import scala.concurrent.Future
import scalaz.\/
import scalaz.std.option.optionSyntax._

class InMemoryNotificationReportRepository extends SentNotificationReportRepository {

  val db = scala.collection.mutable.MutableList.empty[NotificationReport]

  def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    db += report
    Future.successful(\/.right(()))
  }

  def getByUuid(uuid: String): Future[RepositoryResult[NotificationReport]] = {
    Future.successful(db.find(_.notification.uuid == uuid) \/> RepositoryError("Notification report not found"))
  }

  def getByDateRange(from: DateTime, to: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    Future.successful(\/.right(db.filter({report =>
      (report.sentTime isAfter from) && (report.sentTime isBefore to)
    }).toList))
  }
}
