package report.controllers

import java.util.UUID

import authentication.AuthenticationSupport
import models._
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Controller}
import report.services.{Configuration, NotificationReportEnricher}
import tracking.SentNotificationReportRepository

import scala.concurrent.ExecutionContext
import cats.data.{Xor, XorT}
import cats.implicits._

final class Report(
  configuration: Configuration,
  reportRepository: SentNotificationReportRepository,
  reportEnricher: NotificationReportEnricher)
  (implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  val allApiKeys = configuration.apiKeys ++ configuration.electionRestrictedApiKeys

  override def validApiKey(apiKey: String): Boolean = allApiKeys.contains(apiKey)

  override def isPermittedTopic(apiKey: String): Topic => Boolean =
    _ => false

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
        case Xor.Right(result) => Ok(Json.toJson(result))
        case Xor.Left(error) => InternalServerError(error.message)
      }
    }
  }

  def notification(id: UUID): Action[AnyContent] = AuthenticatedAction.async {
    XorT(reportRepository.getByUuid(id)).semiflatMap(reportEnricher.enrich).fold(
      error => InternalServerError(error.message),
      result => Ok(Json.toJson(result))
    )
  }
}
