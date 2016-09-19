package conf

import com.gu.conf.ConfigurationFactory

import scala.concurrent.duration.{Duration, FiniteDuration}

class NotificationConfiguration(projectName: String) {
  lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = projectName,
    webappConfDirectory = "gu-conf"
  )

  def getFiniteDuration(key: String): Option[FiniteDuration] = conf.getStringProperty(key).map(Duration.apply) collect {
    case finite: FiniteDuration => finite
  }

  def getConfigString(key: String): String = conf.getStringProperty(key)
    .getOrElse(throw new RuntimeException(s"key $key not found in configuration"))

  def getConfigInt(key: String): Int = conf.getIntegerProperty(key)
    .getOrElse(throw new RuntimeException(s"key $key not found in configuration"))

  def getConfigInt(key: String, default: Int): Int = conf.getIntegerProperty(key)
    .getOrElse(default)

  def getConfigBoolean(key: String): Boolean = getConfigString(key).toLowerCase match {
    case "true" => true
    case "false" => false
    case _ => throw new RuntimeException(s"key $key has a value that isn't a boolean: ${getConfigString(key)}")
  }
}
