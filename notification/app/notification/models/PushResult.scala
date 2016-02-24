package notification.models

import java.util.UUID
import models.NotificationReport
import play.api.libs.json._

case class PushResult(id: UUID, trackingErrors: Option[List[TrackingError]] = None) {
  def withTrackingErrors(errors: List[TrackingError]): PushResult = copy(trackingErrors = Some(errors))
}

object PushResult {
  def fromReport(report: NotificationReport): PushResult = PushResult(report.notification.id)

  implicit val jf = Json.format[PushResult]
}
