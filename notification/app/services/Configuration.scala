package services

import javax.inject.Inject

import com.gu.conf.ConfigurationFactory

import scala.concurrent.ExecutionContext

case class NotificationHubConfiguration(
  endpointUri: String,
  hubName: String,
  secretKeyName: String,
  secretKeyValue: String
)

case class ErrorMessage(message: String)

final class Configuration @Inject()()(implicit executionContext: ExecutionContext) {

  private lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "notification",
    webappConfDirectory = "gu-conf"
  )

  lazy val notificationHubConfiguration = NotificationHubConfiguration(
    endpointUri= conf.getStringProperty("gu.msnotifications.endpointUri").get,
    hubName = conf.getStringProperty("gu.msnotifications.hubname").get,
    secretKeyName = conf.getStringProperty("gu.msnotifications.secretKeyName").get,
    secretKeyValue = conf.getStringProperty("gu.msnotifications.secretKeyValue").get
  )

  lazy val apiKey = conf.getStringProperty("gu.msnotifications.admin-api-key")
}
