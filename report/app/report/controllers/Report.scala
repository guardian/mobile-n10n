package report.controllers

import javax.inject.Inject

import authentication.AuthenticationSupport
import models.NotificationType
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, AnyContent}
import report.services.{NotificationReportRepositorySupport, Configuration}

import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/-}

final class Report @Inject()(
  configuration: Configuration,
  notificationReportRepositorySupport: NotificationReportRepositorySupport)
  (implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  override def validApiKey(apiKey: String): Boolean = configuration.apiKey.contains(apiKey)

  import notificationReportRepositorySupport._

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  def notifications(notificationType: NotificationType, from: Option[DateTime], until: Option[DateTime]): Action[AnyContent] = AuthenticatedAction.async { request =>
    notificationReportRepository.getByTypeWithDateRange(
      notificationType = notificationType,
      from = from.getOrElse(DateTime.now.minusWeeks(1)),
      until = until.getOrElse(DateTime.now)
    ) map {
      case \/-(result) => Ok(Json.toJson(result))
      case -\/(error) => InternalServerError(error.message)
    }
  }

}
