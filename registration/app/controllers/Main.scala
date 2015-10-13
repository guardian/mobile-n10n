package controllers

import javax.inject.Inject

import gu.msnotifications.HubFailure.{HubInvalidConnectionString, HubParseFailed, HubServiceError}
import models.{ApiResponse, Registration}
import notifications.providers
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{AnyContent, Action, Controller, Result}
import services._

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/, \/-}

final class Main @Inject()(notificationRegistrarSupport: RegistrarSupport)
    (implicit executionContext: ExecutionContext)
  extends Controller {

  import notificationRegistrarSupport._

  private val logger = Logger("main")

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  def register(deviceId: String): Action[Registration] = Action.async(BodyJson[Registration]) { request =>
    val registration = request.body.copy(deviceId = deviceId)
    val registrar = registrarFor(registration)

    registrar match {
      case -\/(msg) => Future.successful(InternalServerError(msg))
      case \/-(r) => r.register(registration).map { processRegistrationResult }
    }
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

}
