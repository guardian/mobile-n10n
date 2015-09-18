package controllers

import javax.inject.Inject

import gu.msnotifications.HubFailure.{HubServiceError, HubParseFailed}
import gu.msnotifications._
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers, Controller, Result}
import services._
import scala.async.Async
import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/-}

final class Main @Inject()(wsClient: WSClient,
                           msNotificationsConfiguration: ApiConfiguration)(
                            implicit executionContext: ExecutionContext
                            ) extends Controller {

  import msNotificationsConfiguration.WriteAction
  import NotificationHubClient.HubResult

  private val logger = Logger("main")

  import msNotificationsConfiguration._

  def healthCheck = Action {
    Ok("Good")
  }

  def dependencies = Action.async {
    Async.async {
      notificationHubOR match {
        case \/-(_) =>
          Async.await(notificationHubClient.fetchRegistrationsListEndpoint) map { _ =>
            Ok("Good")
          } leftMap { other =>
            logger.error(s"Registrations fetch failed: $other")
            InternalServerError("Failed to list registrations")
          } merge
        case -\/(reason) =>
          logger.error(s"Configuration is invalid: $notificationHubOR")
          InternalServerError("Configuration invalid")
      }
    }
  }

  private def processHubResult[T](result: HubResult[T])(implicit tjs: Writes[T]): Result = {
    result match {
      case \/-(json) =>
        Ok(Json.toJson(json))
      case -\/(HubServiceError(reason, code)) =>
        logger.error(message = s"Service error code $code: $reason")
        Status(code.toInt)(s"Upstream service failed with code $code.")
      case -\/(HubParseFailed(body, reason)) =>
        logger.error(message = s"Failed to parse body due to: $reason; body = $body")
        InternalServerError(reason)
    }
  }

  def push = WriteAction.async(BodyParsers.parse.json[AzureXmlPush]) { request =>
    Async.async {
      Async.await {
        notificationHubClient.sendPush(request.body)
      } match {
        case \/-(_) =>
          Ok("Ok")
        case -\/(HubServiceError(reason, code)) =>
          logger.error(message = s"Service error code $code: $reason")
          Status(code.toInt)(s"Upstream service failed with code $code.")
        case -\/(HubParseFailed(body, reason)) =>
          logger.error(message = s"Failed to parse body due to: $reason; body = $body")
          InternalServerError(reason)
      }
    }
  }

  def register(registrationId: RegistrationId) =
    Action.async(BodyParsers.parse.json[WindowsRegistration]) { request =>
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