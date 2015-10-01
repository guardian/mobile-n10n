package controllers

import javax.inject.Inject

import models.{UserId, Notification, Topic, Push}
import notifications.providers.{Error => ProviderError}
import play.api.libs.ws.WSClient
import play.api.mvc.{Result, Action, BodyParsers, Controller}
import services._
import scala.concurrent.ExecutionContext
import BodyParsers.parse.{json => BodyJson}

import scalaz.{-\/, \/-}

final class Main @Inject()(wsClient: WSClient, msNotificationsConfiguration: NotificationConfiguration)
  (implicit executionContext: ExecutionContext)
  extends Controller {

  import msNotificationsConfiguration._

  val provider = msNotificationsConfiguration.notificationHubClient

  def handleErrors[T](result: T): Result = result match {
    case error: ProviderError => InternalServerError(error.reason)
  }

  def healthCheck = Action {
    Ok("Good")
  }

  def pushTopic(topic: Topic) = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Left(topic))
    provider.sendNotification(push) map {
      case \/-(_) => Ok("Ok")
      case -\/(error) => handleErrors(error)
    }
  }

  def pushUser(userId: String) = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Right(UserId(userId)))
    provider.sendNotification(push) map {
      case \/-(_) => Ok("Ok")
      case -\/(error) => handleErrors(error)
    }
  }

}