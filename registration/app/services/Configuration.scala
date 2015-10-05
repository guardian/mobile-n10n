package services

import javax.inject.Inject

import com.gu.conf.ConfigurationFactory

import scala.concurrent.ExecutionContext

case class NotificationHubConfiguration(
  connectionString: String,
  hubName: String
)

case class ErrorMessage(message: String)

final class Configuration @Inject()()(implicit executionContext: ExecutionContext) {

  private lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "registration",
    webappConfDirectory = "gu-conf"
  )

  lazy val notificationHubConfiguration = NotificationHubConfiguration(
    connectionString = conf.getStringProperty("gu.msnotifications.connectionstring").get,
    hubName = conf.getStringProperty("gu.msnotifications.hubname").get
  )
}
