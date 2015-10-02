package controllers

import javax.inject.Inject

import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}
import services._
import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/-}

final class Report @Inject()(wsClient: WSClient, msNotificationsConfiguration: ReportConfiguration)
  (implicit executionContext: ExecutionContext)
  extends Controller {

  import msNotificationsConfiguration._

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