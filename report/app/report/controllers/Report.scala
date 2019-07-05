package report.controllers

import java.util.UUID

import authentication.AuthAction
import models._
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc._
import report.services.Configuration
import tracking.SentNotificationReportRepository

import scala.concurrent.ExecutionContext
import cats.data.EitherT
import cats.implicits._

final class Report(
  configuration: Configuration,
  controllerComponents: ControllerComponents,
  reportRepository: SentNotificationReportRepository,
  authAction: AuthAction )
  (implicit executionContext: ExecutionContext)
  extends AbstractController(controllerComponents)  {

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  def notifications(notificationType: NotificationType, from: Option[DateTime], until: Option[DateTime], includeDryRun: Option[Boolean]): Action[AnyContent] = {
    authAction.async { request =>

      reportRepository.getByTypeWithDateRange(
        notificationType = notificationType,
        from = from.getOrElse(DateTime.now.minusWeeks(1)),
        to = until.getOrElse(DateTime.now)
      ) map {
        case Right(result) => {
          val includeDryRunBoolean: Boolean = includeDryRun.contains(true)
          val filteredResults = result.filter(report => report.notification.dryRun.contains(true) == includeDryRunBoolean)
          Ok(Json.toJson(filteredResults))
        }
        case Left(error) => InternalServerError(error.message)
      }
    }
  }

  def notification(id: UUID): Action[AnyContent] = authAction.async {
    EitherT(reportRepository.getByUuid(id)).map(ExtendedNotificationReport.fromNotificationReport).fold(
      error => InternalServerError(error.message),
      result => Ok(Json.toJson(result))
    )
  }
}
