package batches

import javax.inject.Singleton

import com.gu.conf.ConfigurationFactory
import gu.msnotifications.NotificationHubConnection

@Singleton
class BackupConfiguration {

  lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "backup",
    webappConfDirectory = "gu-conf"
  )

  def getConfigString(key: String): String = conf.getStringProperty(key)
    .getOrElse(throw new RuntimeException(s"key $key not found in configuration"))
  def getConfigInt(key: String): Int = conf.getIntegerProperty(key)
    .getOrElse(throw new RuntimeException(s"key $key not found in configuration"))

  lazy val storageConnectionString = getConfigString("azure.storageConnectionString")
  lazy val containerName = getConfigString("azure.storageContainerName")
  lazy val directoryName = getConfigString("azure.storageDirectoryName")
  lazy val backupRetention = getConfigInt("azure.backupRetention")
  lazy val notificationHubConnection = NotificationHubConnection(
    getConfigString("azure.url"),
    getConfigString("azure.secretKeyName"),
    getConfigString("azure.secretKey"))
}
