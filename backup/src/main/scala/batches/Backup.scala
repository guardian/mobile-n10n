package batches

import java.util
import javax.inject.Inject

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions._
import com.microsoft.azure.storage.blob.SharedAccessBlobPolicy
import gu.msnotifications.{NotificationHubJobType, _}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.ws.WSClient
import play.api.{Logger, Application}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/-}

class Backup @Inject() (app: Application, ws: WSClient)(implicit ec: ExecutionContext) extends Batch {

  def getConfigString(key: String): String = app.configuration.getString(key)
    .getOrElse(throw new RuntimeException(s"key $key not found in configuration"))

  val storageConnectionString = getConfigString("azure.storageConnectionString")
  val containerName = getConfigString("azure.storageContainerName")
  val notificationHubConnection = NotificationHubConnection(
    getConfigString("azure.url"),
    getConfigString("azure.secretKeyName"),
    getConfigString("azure.secretKey"))

  def execute(): Future[Unit] = {
    val storageAccount = CloudStorageAccount.parse(storageConnectionString)
    val blobClient = storageAccount.createCloudBlobClient()
    val container = blobClient.getContainerReference(containerName)
    container.createIfNotExists()

    val sharedAccessPolicy = new SharedAccessBlobPolicy()
    sharedAccessPolicy.setPermissions(util.EnumSet.of(READ, WRITE, LIST))
    sharedAccessPolicy.setSharedAccessExpiryTime(DateTime.now(DateTimeZone.UTC).plusHours(3).toDate)
    val arg = container.generateSharedAccessSignature(sharedAccessPolicy, "")
    val containerUri = container.getUri + "?" + arg

    val notificationJob = NotificationHubJobRequest(
      jobType = NotificationHubJobType.ExportRegistrations,
      outputContainerUri = Some(containerUri)
    )

    Logger.info(notificationJob.toXml.toString())
    val hubClient = new NotificationHubClient(notificationHubConnection, ws)
    hubClient.submitNotificationHubJob(notificationJob).map {
      case \/-(result) => result.items.foreach { job =>
        Logger.info(s"Success, job created with id ${job.jobId}\n$job")
      }
      case -\/(failure) => Logger.error(failure.toString)
    }
  }
}
