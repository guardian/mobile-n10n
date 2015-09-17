package services

import javax.inject.Inject

import gu.msnotifications.{NotificationHubClient, ConnectionSettings}
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc.{Results, Result, Request, ActionBuilder}

import scala.concurrent.{Future, ExecutionContext}
import scalaz.\/

case class ErrorMessage(message: String)

final class ApiConfiguration @Inject()(configuration: Configuration, wsClient: WSClient)
                                      (implicit executionContext: ExecutionContext) {

  private def getConfigurationProperty(name: String): ErrorMessage \/ String  = {
    configuration.getString(name) match {
      case Some(value) => \/.right(value)
      case None => \/.left(ErrorMessage("Could not find property $name"))
    }
  }

  def notificationHubOR = for {
    connectionString <- getConfigurationProperty("gu.msnotifications.connectionstring")
    hubName <- getConfigurationProperty("gu.msnotifications.hubname")
    settings <- ConnectionSettings.fromString(connectionString).leftMap(failure => ErrorMessage(failure.reason))
  } yield settings.buildNotificationHub(hubName)

  private def notificationHub = notificationHubOR.fold(error => throw new Exception(error.message), identity)

  def notificationHubClient = new NotificationHubClient(notificationHub, wsClient)

  def WriteAction: ActionBuilder[Request] = new ActionBuilder[Request] with Results {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      request.getQueryString("api-key") match {
        case Some(apiKey) if validApiKey(apiKey) => block(request)
        case _ => Future.successful(Unauthorized("A valid API key is required."))
      }
    }
  }

  private def validApiKey(apiKey: String): Boolean = {
    getConfigurationProperty("gu.msnotifications.admin-api-key").toOption.contains(apiKey)
  }

}
