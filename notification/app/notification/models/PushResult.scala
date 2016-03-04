package notification.models

import java.util.UUID
import models.NotificationReport
import notification.services.NotificationRejected
import play.api.libs.json._
import tracking.RepositoryError

case class PushResult(
  id: UUID,
  reportingError: Option[String] = None,
  rejectedNotifications: Option[List[String]] = None
) {
  def withRejected(rejected: List[NotificationRejected]) = copy(rejectedNotifications = Some(rejected map { _.toString }))

  def withReportingError(error: RepositoryError) = copy(reportingError = Some(error.message))
}

object PushResult {
  def fromReport(report: NotificationReport): PushResult = PushResult(report.notification.id)

  implicit val jf = Json.format[PushResult]
}
