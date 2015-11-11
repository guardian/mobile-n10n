package batches

import javax.inject.Singleton

import conf.NotificationConfiguration
import gu.msnotifications.NotificationHubConnection

@Singleton
class BackupConfiguration extends NotificationConfiguration("backup") {
  lazy val storageConnectionString = getConfigString("azure.storageConnectionString")
  lazy val containerName = getConfigString("azure.storageContainerName")
  lazy val directoryName = getConfigString("azure.storageDirectoryName")
  lazy val backupRetention = getConfigInt("azure.backupRetention")
  lazy val notificationHubConnection = NotificationHubConnection(
    getConfigString("azure.url"),
    getConfigString("azure.secretKeyName"),
    getConfigString("azure.secretKey"))
}
