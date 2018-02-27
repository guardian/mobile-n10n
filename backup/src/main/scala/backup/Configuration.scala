package backup

import azure.NotificationHubConnection
import backup.logging.BackupLogging
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.gu.AwsIdentity
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.typesafe.config.Config

case class Configuration(
  storageConnectionString: String,
  containerName: String,
  directoryName: String,
  backupRetention: Int,
  notificationHubConnection: NotificationHubConnection
)

object Configuration extends BackupLogging {

  def load(credentials: AWSCredentialsProvider): Configuration = {
    val bucket: String = getEnvironmentValueOrDie("ConfigurationBucket", "No S3 Configuration bucket provided")
    val configurationKey: String = getEnvironmentValueOrDie("ConfigurationKey", "No S3 key for configuration provided")
    val app: String = getEnvironmentValueOrDie("App", "No app tag set")
    val stack: String = getEnvironmentValueOrDie("Stack", "No stack tag set")
    val stage: String = getEnvironmentValueOrDie("Stage", "No stage tag set")

    val identity = new AwsIdentity(app, stack, stage, Regions.EU_WEST_1.getName)
    val config = ConfigurationLoader.load(identity, credentials) {
      case AwsIdentity(app, stack, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/$stack")
    }

    logger.info(s"Loading config. Stage: $stage Bucket: $bucket Key: $configurationKey")

    val storageConnectionString = config.getString("azure.storage.connectionString")
    logger.info(s"storageConnectionKey: $storageConnectionString")
    val containerName = config.getString("azure.storage.containerName")
    logger.info(s"containerName: $containerName")
    val directoryName = config.getString("azure.storage.directoryName")
    logger.info(s"azure.storage.directoryName: $directoryName")
    val backupRetention = config.getInt("backup.retentionInDays")
    logger.info(s"backup.retentionInDays $directoryName")
    val notificationHubConnection = getNotificationHubConnection(config)

    Configuration(
      storageConnectionString,
      containerName,
      directoryName,
      backupRetention,
      notificationHubConnection
    )
  }
  private def getNotificationHubConnection(config: Config) : NotificationHubConnection = {
    val endpoint = config.getString("azure.hub.endpoint")
    logger.info(s"azure.hub.endpoint: $endpoint")
    val sharedAccessKeyName = config.getString("azure.hub.sharedAccessKeyName")
    val sharedAccessKey = config.getString("azure.hub.sharedAccessKey")

    NotificationHubConnection(endpoint, sharedAccessKeyName, sharedAccessKey)

  }

  private def getEnvironmentValueOrDie(configurationKey: String, errorMessage: String): String = {
    Option(System.getenv(configurationKey)).getOrElse(sys.error(s"$errorMessage. Lambda will not run"))
  }

}
