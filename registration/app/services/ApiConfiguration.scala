package services

import javax.inject.Inject

import gu.msnotifications.{NotificationHubClient, ConnectionString, NotificationHubConnection}
import play.api.Configuration
import org.scalactic._
import org.scalactic.Accumulation._
import play.api.libs.ws.WSClient
import play.api.mvc.{Results, Result, Request, ActionBuilder}

import scala.concurrent.{Future, ExecutionContext}

final class ApiConfiguration @Inject()(configuration: Configuration, wsClient: WSClient)
                                      (implicit executionContext: ExecutionContext) {

  private def getConfigurationProperty(name: String): String Or One[ErrorMessage] = {
    configuration.getString(name) match {
      case Some(value) => Good(value)
      case None => Bad(One(s"Could not find property $name"))
    }
  }

  def notificationHubOR = {
    withGood(
      getConfigurationProperty("gu.msnotifications.connectionstring").map(ConnectionString.apply),
      getConfigurationProperty("gu.msnotifications.hubname")
    ) { (connectionString, hubName) => connectionString.buildNotificationHub(hubName) }
  }.flatMap(identity) // scalactic is missing .flatten?

  private def notificationHub = notificationHubOR.get

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
