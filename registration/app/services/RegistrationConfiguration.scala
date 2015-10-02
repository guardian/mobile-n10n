package services

import javax.inject.Inject

import gu.msnotifications.{NotificationHubClient, ConnectionSettings}
import play.api.libs.ws.WSClient
import play.api.mvc.{Results, Result, Request, ActionBuilder}
import tracking.InMemoryTopicSubscriptionsRepository

import scala.concurrent.{Future, ExecutionContext}
import scalaz.\/
import com.gu.conf.ConfigurationFactory
import scalaz.std.option.optionSyntax._

case class ErrorMessage(message: String)

final class RegistrationConfiguration @Inject()(wsClient: WSClient)
                                      (implicit executionContext: ExecutionContext) {

  private lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "registration",
    webappConfDirectory = "gu-conf"
  )

  private def getConfigurationProperty(name: String): ErrorMessage \/ String =
    conf.getStringProperty(name) \/> ErrorMessage(s"Could not find property $name")

  def notificationHubOR = for {
    connectionString <- getConfigurationProperty("gu.msnotifications.connectionstring")
    hubName <- getConfigurationProperty("gu.msnotifications.hubname")
    settings <- ConnectionSettings.fromString(connectionString).leftMap(failure => ErrorMessage(failure.reason))
  } yield settings.buildNotificationHub(hubName)

  private def notificationHub = notificationHubOR.fold(error => throw new Exception(error.message), identity)

  val topicSubscriptionRepository = new InMemoryTopicSubscriptionsRepository
  def notificationHubClient = new NotificationHubClient(notificationHub, wsClient, topicSubscriptionRepository)

  private def validApiKey(apiKey: String): Boolean = {
    getConfigurationProperty("gu.msnotifications.admin-api-key").toOption.contains(apiKey)
  }

}
