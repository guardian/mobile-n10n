package conf

import com.gu.conf.ConfigurationFactory

class NotificationConfiguration(projectName: String) {
  lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = projectName,
    webappConfDirectory = "gu-conf"
  )

  def getConfigString(key: String): String = conf.getStringProperty(key)
    .getOrElse(throw new RuntimeException(s"key $key not found in configuration"))

  def getConfigInt(key: String): Int = conf.getIntegerProperty(key)
    .getOrElse(throw new RuntimeException(s"key $key not found in configuration"))
}
