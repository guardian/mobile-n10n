package tracking

import java.util.UUID
import models.{NotificationType, DynamoNotificationReport}
import org.joda.time.DateTime
import tracking.Repository.RepositoryResult
import scala.concurrent.Future

trait SentNotificationReportRepository {

  def store(report: DynamoNotificationReport): Future[RepositoryResult[Unit]]

  def update(report: DynamoNotificationReport): Future[RepositoryResult[Unit]]

  def getByUuid(uuid: UUID): Future[RepositoryResult[DynamoNotificationReport]]

  def getByTypeWithDateRange(notificationType: NotificationType, from: DateTime, to: DateTime): Future[RepositoryResult[List[DynamoNotificationReport]]]
}
