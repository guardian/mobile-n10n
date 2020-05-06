package tracking

import java.util.UUID
import models.{NotificationReport, NotificationType}
import org.joda.time.{DateTime, Interval}
import tracking.Repository.RepositoryResult

import scala.concurrent.Future
import cats.implicits._

class InMemoryNotificationReportRepository extends SentNotificationReportRepository {

  val db = scala.collection.mutable.ArrayBuffer.empty[NotificationReport]

  override def store(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    db += report
    Future.successful(Right(()))
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[NotificationReport]] = {
    Future.successful(Either.fromOption(db.find(_.id == uuid), RepositoryError("Notification report not found")))
  }

  override def getByTypeWithDateRange(`type`: NotificationType, from: DateTime, until: DateTime): Future[RepositoryResult[List[NotificationReport]]] = {
    val interval = new Interval(from, until)
    Future.successful(Right(
      db.filter({report =>
        report.`type` == `type` && (interval contains report.sentTime)
      }).toList
    ))
  }

  override def update(report: NotificationReport): Future[RepositoryResult[Unit]] = {
    db.mapInPlace {
      case existingReport if existingReport.id == report.id && existingReport.reports == Nil => report
      case report => report
    }
    Future.successful(Right(()))
  }
}
