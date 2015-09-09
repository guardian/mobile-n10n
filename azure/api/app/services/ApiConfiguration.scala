package services

import javax.inject.Inject

import gu.msnotifications.{NotificationHubClient, ConnectionString, NotificationHubConnection}
import play.api.Configuration
import org.scalactic._
import org.scalactic.Accumulation._
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

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

  def notificationHub = notificationHubOR.get

  def notificationHubClient = new NotificationHubClient(notificationHub, wsClient)

}
