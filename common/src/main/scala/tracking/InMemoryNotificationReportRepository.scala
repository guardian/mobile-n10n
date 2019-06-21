package tracking

import java.util.UUID
import models.{DynamoNotificationReport, NotificationType}
import org.joda.time.{DateTime, Interval}
import tracking.Repository.RepositoryResult

import scala.concurrent.Future
import cats.implicits._

class InMemoryNotificationReportRepository extends SentNotificationReportRepository {

  val db = scala.collection.mutable.MutableList.empty[DynamoNotificationReport]

  override def store(report: DynamoNotificationReport): Future[RepositoryResult[Unit]] = {
    db += report
    Future.successful(Right(()))
  }

  override def getByUuid(uuid: UUID): Future[RepositoryResult[DynamoNotificationReport]] = {
    Future.successful(Either.fromOption(db.find(_.id == uuid), RepositoryError("Notification report not found")))
  }

  override def getByTypeWithDateRange(`type`: NotificationType, from: DateTime, until: DateTime): Future[RepositoryResult[List[DynamoNotificationReport]]] = {
    val interval = new Interval(from, until)
    Future.successful(Right(
      db.filter({report =>
        report.`type` == `type` && (interval contains report.sentTime)
      }).toList
    ))
  }

  override def update(report: DynamoNotificationReport): Future[RepositoryResult[Unit]] = {
    db.transform {
      case existingReport if existingReport.id == report.id && existingReport.reports == Nil => report
      case report => report
    }
    Future.successful(Right(()))
  }
}
