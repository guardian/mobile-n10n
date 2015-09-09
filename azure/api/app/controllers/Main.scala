package controllers

import javax.inject.Inject

import gu.msnotifications._
import org.scalactic.{Good, Bad}
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers, Controller, Result}
import HubResult.{ServiceError, ServiceParseFailed, Successful}
import services._
import scala.async.Async
import scala.concurrent.ExecutionContext

final class Main @Inject()(wsClient: WSClient,
                           msNotificationsConfiguration: ApiConfiguration)(
                            implicit executionContext: ExecutionContext
                            ) extends Controller {

  private val logger = Logger("main")

  import msNotificationsConfiguration._

  def healthCheck = Action.async {
    Async.async {
      notificationHubOR match {
        case Good(_) =>
          Async.await(notificationHubClient.fetchRegistrationsListEndpoint) match {
            case Successful("Registrations") =>
              Ok("Good")
            case other =>
              logger.error(s"Registrations fetch failed: $other")
              InternalServerError("Failed to list registrations")
          }
        case Bad(reason) =>
          logger.error(s"Configuration is invalid: $notificationHubOR")
          InternalServerError("Configuration invalid")
      }
    }
  }

  implicit val registrationIdWrites = Json.writes[RegistrationId]

  def processHubResult[T](result: HubResult[T])(implicit tjs: Writes[T]): Result = {
    result match {
      case Successful(json) =>
        Ok(Json.toJson(json))
      case ServiceError(reason, code) =>
        logger.error(message = s"Service error code $code: $reason")
        Status(code.toInt)(s"Upstream service failed with code $code.")
      case ServiceParseFailed(body, reason) =>
        logger.error(message = s"Failed to parse body due to: $reason; body = $body")
        InternalServerError(reason)
    }
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

  def push = Action.async(BodyParsers.parse.json[AzureXmlPush]) { request =>
    Async.async {
      Async.await {
        notificationHubClient.sendPush(request.body)
      } match {
        case Successful(_) =>
          Ok("Ok")
        case ServiceError(reason, code) =>
          logger.error(message = s"Service error code $code: $reason")
          Status(code.toInt)(s"Upstream service failed with code $code.")
        case ServiceParseFailed(body, reason) =>
          logger.error(message = s"Failed to parse body due to: $reason; body = $body")
          InternalServerError(reason)
      }
    }
  }

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