package services

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import com.gu.conf.ConfigurationFactory
import scalaz.\/
import scalaz.std.option.optionSyntax._

case class ErrorMessage(message: String)

final class NotificationConfiguration @Inject() (implicit executionContext: ExecutionContext) {

  private lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "notification",
    webappConfDirectory = "gu-conf"
  )

  private def getConfigurationProperty(name: String): ErrorMessage \/ String =
    conf.getStringProperty(name) \/> ErrorMessage(s"Could not find property $name")

}
