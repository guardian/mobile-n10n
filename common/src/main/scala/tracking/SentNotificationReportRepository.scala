package tracking

import java.util.UUID
import models.{NotificationType, NotificationReport}
import org.joda.time.DateTime
import tracking.Repository.RepositoryResult
import scala.concurrent.Future

trait SentNotificationReportRepository {

  def store(report: NotificationReport): Future[RepositoryResult[Unit]]

  def update(report: NotificationReport): Future[RepositoryResult[Unit]]

  def getByUuid(uuid: UUID): Future[RepositoryResult[NotificationReport]]

  def getByTypeWithDateRange(notificationType: NotificationType, from: DateTime, to: DateTime): Future[RepositoryResult[List[NotificationReport]]]
}
