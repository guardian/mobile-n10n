package notification.models

import java.util.UUID

import models.DynamoNotificationReport
import notification.services.SenderError
import play.api.libs.json._
import tracking.RepositoryError

case class PushResult(
  id: UUID,
  reportingError: Option[String] = None,
  rejectedNotifications: Option[List[String]] = None
) {
  def withRejected(rejected: List[SenderError]) = copy(rejectedNotifications = Some(rejected map { _.toString }))

  def withReportingError(error: RepositoryError) = copy(reportingError = Some(error.message))
}

object PushResult {
  def fromReport(report: DynamoNotificationReport): PushResult = PushResult(report.notification.id)

  implicit val jf = Json.format[PushResult]
}
