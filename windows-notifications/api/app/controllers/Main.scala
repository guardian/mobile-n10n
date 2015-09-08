package controllers

import javax.inject.Inject

import gu.msnotifications.{RegistrationId, WindowsRegistration}
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers, Controller, Result}
import services.HubResult.{ServiceError, ServiceParseFailed, Successful}
import services._
import scala.async.Async
import scala.concurrent.ExecutionContext

final class Main @Inject()(wsClient: WSClient,
                           msNotificationsConfiguration: ApiConfiguration,
                           notificationHubClient: NotificationHubClient)(
                            implicit executionContext: ExecutionContext
                            ) extends Controller {

  private val logger = Logger("main")

  import msNotificationsConfiguration._

  def healthCheck = Action {
    if (notificationHubOR.isGood) {
      Ok("Good")
    } else {
      logger.error(s"Configuration is invalid: $notificationHubOR")
      InternalServerError("Configuration invalid")
    }
  }

  implicit val registrationIdWrites = Json.writes[RegistrationId]

  def processHubResult[T](result: HubResult[T])(implicit tjs: Writes[T]): Result = {
    result match {
      case Successful(registrationId) =>
        Ok(Json.toJson(registrationId))
      case ServiceError(reason, code) =>
        logger.error(message = s"Service error code $code: $reason")
        Status(code.toInt)(s"Upstream service failed with code $code.")
      case ServiceParseFailed(body, reason) =>
        logger.error(message = s"Failed to parse body due to: $reason; body = $body")
        InternalServerError(reason)
    }
  }

  def fetchSome = Action.async {
    notificationHubClient.fetchSomeRegistrations.map(xml => Ok(xml))
  }

  def register = Action.async(BodyParsers.parse.json[WindowsRegistration]) { request =>
    Async.async {
      processHubResult {
        Async.await {
          notificationHubClient.register(
            rawWindowsRegistration = request.body.toRaw
          )
        }
      }
    }
  }

  def push = TODO

  def update(registrationId: RegistrationId) = Action.async(BodyParsers.parse.json[WindowsRegistration]) { request =>
    Async.async {
      processHubResult {
        Async.await {
          notificationHubClient.update(
            registrationId = registrationId,
            rawWindowsRegistration = request.body.toRaw
          )
        }
      }
    }
  }

}