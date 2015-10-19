package batches

import java.util
import javax.inject.Inject

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions._
import com.microsoft.azure.storage.blob._
import gu.msnotifications.{NotificationHubJobType, _}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.ws.WSClient
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/-}

class Backup @Inject() (conf: BackupConfiguration, ws: WSClient)(implicit ec: ExecutionContext) extends Batch {

  def execute(): Future[Unit] = {
    val storageAccount = CloudStorageAccount.parse(conf.storageConnectionString)
    val blobClient = storageAccount.createCloudBlobClient()
    val container = blobClient.getContainerReference(conf.containerName)
    container.createIfNotExists()

    cleanup(container)

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
    val hubClient = new NotificationHubClient(conf.notificationHubConnection, ws)
    hubClient.submitNotificationHubJob(notificationJob).map {
      case \/-(result) => result.items.foreach { job =>
        Logger.info(s"Success, job created with id ${job.jobId}\n$job")
      }
      case -\/(failure) => Logger.error(failure.toString)
    }
  }

  private def cleanup(container: CloudBlobContainer): Unit = {
    val tooOld = DateTime.now().minusDays(conf.backupRetention).toDate
    val directory = container.getDirectoryReference(conf.directoryName)
    val allBlobs = AzureUtils.listAllFiles(directory)
    val blobsToDelete = allBlobs.filter(_.getProperties.getLastModified.before(tooOld))
    blobsToDelete.foreach { blob =>
      blob.deleteIfExists()
      Logger.info(s"deleted old backup: ${blob.getName}")
    }
  }
}
