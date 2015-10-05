package controllers

import javax.inject.Inject

import gu.msnotifications.HubFailure.{HubInvalidConnectionString, HubServiceError, HubParseFailed}
import models.{Registration, ApiResponse, WindowsMobile}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, BodyParsers, Controller, Result}
import services._
import scala.concurrent.{Future, ExecutionContext}
import scalaz.{\/, -\/, \/-}
import BodyParsers.parse.{json => BodyJson}
import notifications.providers

final class Main @Inject()(notificationRegistrarSupport: NotificationRegistrarSupport)
    (implicit executionContext: ExecutionContext)
  extends Controller {

  private val logger = Logger("main")

  import notificationRegistrarSupport._

  def healthCheck = Action {
    Ok("Good")
  }

  private def processRegistrationResult[T](result: providers.Error \/ T): Result = {
    result match {
      case \/-(json) =>
        Ok(Json.toJson(ApiResponse("success")))
      case -\/(HubServiceError(reason, code)) =>
        logger.error(message = s"Service error code $code: $reason")
        Status(code.toInt)(s"Upstream service failed with code $code.")
      case -\/(HubParseFailed(body, reason)) =>
        logger.error(message = s"Failed to parse body due to: $reason; body = $body")
        InternalServerError(reason)
      case -\/(HubInvalidConnectionString(reason)) =>
        logger.error(message = s"Failed due to invalid connection string: $reason")
        InternalServerError(reason)
      case -\/(other) =>
        logger.error(message = s"Unknown error: ${other.reason}")
        InternalServerError(other.reason)
    }
  }

  def register(deviceId: String) = Action.async(BodyJson[Registration]) { request =>
    if (request.body.platform == WindowsMobile) {
      notificationRegistrar.register(request.body.copy(deviceId = deviceId)).map(processRegistrationResult)
    } else {
      Future.successful(InternalServerError("Platform not supported"))
    }
  }
}