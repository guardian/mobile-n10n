package services

import javax.inject.Inject

import com.gu.conf.ConfigurationFactory

import scala.concurrent.ExecutionContext
import scalaz.\/
import scalaz.std.option.optionSyntax._

case class NotificationHubConfiguration(
  connectionString: String,
  hubName: String
)

case class ErrorMessage(message: String)

final class Configuration @Inject()()(implicit executionContext: ExecutionContext) {

  private lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "notification",
    webappConfDirectory = "gu-conf"
  )

  private def getConfigurationProperty(name: String): ErrorMessage \/ String =
    conf.getStringProperty(name) \/> ErrorMessage(s"Could not find property $name")

  lazy val notificationHubConfiguration = for {
    connectionString <- getConfigurationProperty("gu.msnotifications.connectionstring")
    hubName <- getConfigurationProperty("gu.msnotifications.hubname")
  } yield NotificationHubConfiguration(connectionString, hubName)
}
