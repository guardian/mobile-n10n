package tracking

import models.NotificationReport
import org.joda.time.{DateTime, Interval}
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
    Future.successful(db.find(_.uuid == uuid) \/> RepositoryError("Notification report not found"))
  }

  def getByTypeWithDateRange(notificationType: String, from: DateTime, until: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    val interval = new Interval(from, until)
    Future.successful(\/.right(db.filter({report =>
      report.`type` == notificationType && (interval contains report.sentTime)
    }).toList))
  }
}
