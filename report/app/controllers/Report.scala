package controllers

import javax.inject.Inject

import authentication.AuthenticationSupport
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import services._
import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/-}

final class Report @Inject()(
  configuration: Configuration,
  notificationReportRepositorySupport: NotificationReportRepositorySupport)
  (implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  override def validApiKey(apiKey: String) = configuration.apiKey.contains(apiKey)

  import notificationReportRepositorySupport._

  def healthCheck = Action {
    Ok("Good")
  }

  def notifications(from: Option[DateTime], until: Option[DateTime]) = AuthenticatedAction.async { request =>
    notificationReportRepository.getByDateRange(
      from = from.getOrElse(DateTime.now.minusDays(7)),
      until = until.getOrElse(DateTime.now)
    ) map {
      case \/-(result) => Ok(Json.toJson(result))
      case -\/(error) => InternalServerError(error.message)
    }
  }

}