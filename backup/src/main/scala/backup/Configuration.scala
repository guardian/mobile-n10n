package backup

import java.util.Properties
import javax.inject.Singleton

import azure.NotificationHubConnection
import backup.logging.BackupLogging
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import conf.NotificationConfiguration

import scala.util.Try

@Singleton
case class Configuration(
  storageConnectionString: String,
  containerName: String,
  directoryName: String,
  backupRetention: Int,
  notificationHubConnection: NotificationHubConnection
)

object Configuration extends BackupLogging {
  val bucket = getEnvironmentValueOrDie("ConfigurationBucket", "No S3 Configuration bucket provided")
  val configurationKey = getEnvironmentValueOrDie("ConfigurationKey", "No S3 key for configuration provided")
  val stage = getEnvironmentValueOrDie("Stage", "No stage set")

  val s3 = AmazonS3ClientBuilder.standard()
      .withRegion(Regions.EU_WEST_1)
      .build()

  def load(): Configuration = {

    logger.info(s"Loading config. Stage: ${stage} Bucket: ${bucket} Key: ${configurationKey}")
    val properties = loadProperties(bucket, configurationKey) getOrElse sys.error("Could not get properties from s3. Lambda will not run")

    val storageConnectionString = getMandatoryStringProperty(properties, "azure.storage.connectionString")
    logger.info(s"storageConnectionKey: $storageConnectionString")
    val containerName = getMandatoryStringProperty(properties, "azure.storage.containerName")
    logger.info(s"containerName: $containerName")
    val directoryName = getMandatoryStringProperty(properties, "azure.storage.directoryName")
    logger.info(s"azure.storage.directoryName: $directoryName")
    val backupRetention = getMandatoryStringProperty(properties, "backup.retentionInDays").toInt
    logger.info(s"backup.retentionInDays $directoryName")
    val notificationHubConnection = getNotificationHubConnection(properties)

    Configuration(
      storageConnectionString,
      containerName,
      directoryName,
      backupRetention,
      notificationHubConnection
    )
  }
  private def getNotificationHubConnection(properties: Properties) : NotificationHubConnection = {
    val endpoint = getMandatoryStringProperty(properties, "azure.hub.endpoint")
    logger.info(s"azure.hub.endpoint: ${endpoint}")
    val sharedAccessKeyName = getMandatoryStringProperty(properties, "azure.hub.sharedAccessKeyName")
    logger.info(s"azure.hub.sharedAccessKeyName: ${sharedAccessKeyName}")
    val sharedAccessKey = getMandatoryStringProperty(properties, "azure.hub.sharedAccessKey")
    logger.info(s"azure.hub.sharedAccessKey: ${sharedAccessKey}")

    NotificationHubConnection(endpoint, sharedAccessKeyName, sharedAccessKey)

  }

  private def getEnvironmentValueOrDie(configurationKey: String, errorMessage: String): String = {
    Option(System.getenv(configurationKey)).getOrElse(sys.error(s"${errorMessage}. Lambda will not run"))
  }

  private def loadProperties(bucket: String, key: String): Try[Properties] = {
    val stream = s3.getObject(bucket, key).getObjectContent
    val properties = new Properties()
    val result = Try(properties.load(stream)).map(_ => properties)
    stream.close()
    result
  }

  private def getProperty(properties: Properties, key: String) = Option(properties.getProperty(key))

  private def getMandatoryStringProperty(properties: Properties, key: String) = getProperty(properties, key) getOrElse sys.error(s"Property: '${key}' missing. Lambda will not run")

}
