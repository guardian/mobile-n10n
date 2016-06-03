package report.controllers

import authentication.AuthenticationSupport
import models.NotificationType
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, AnyContent}
import report.services.Configuration
import tracking.SentNotificationReportRepository

import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/-}

final class Report(
  configuration: Configuration,
  reportRepository: SentNotificationReportRepository)
  (implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  override def validApiKey(apiKey: String): Boolean = configuration.apiKeys.contains(apiKey)


  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  def notifications(notificationType: NotificationType, from: Option[DateTime], until: Option[DateTime]): Action[AnyContent] = {
    AuthenticatedAction.async { request =>
      reportRepository.getByTypeWithDateRange(
        notificationType = notificationType,
        from = from.getOrElse(DateTime.now.minusWeeks(1)),
        to = until.getOrElse(DateTime.now)
      ) map {
        case \/-(result) => Ok(Json.toJson(result))
        case -\/(error) => InternalServerError(error.message)
      }
    }
  }

}
