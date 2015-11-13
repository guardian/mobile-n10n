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
import collection.JavaConversions._

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/-}

class Backup @Inject() (conf: Configuration, ws: WSClient)(implicit ec: ExecutionContext) extends Batch {

  val batchExecutionDeadline = DateTime.now(DateTimeZone.UTC).plusHours(3)

  def execute(): Future[Unit] = {
    val storageAccount = CloudStorageAccount.parse(conf.storageConnectionString)
    val blobClient = storageAccount.createCloudBlobClient()
    val container = blobClient.getContainerReference(conf.containerName)
    container.createIfNotExists()

    cleanup(container)

    val sharedAccessPolicy = new SharedAccessBlobPolicy()
    sharedAccessPolicy.setPermissions(util.EnumSet.of(READ, WRITE, LIST))
    sharedAccessPolicy.setSharedAccessExpiryTime(batchExecutionDeadline.toDate)
    val arg = container.generateSharedAccessSignature(sharedAccessPolicy, "")
    val containerUri = container.getUri + "?" + arg

    val notificationJob = NotificationHubJobRequest(
      jobType = NotificationHubJobType.ExportRegistrations,
      outputContainerUri = Some(containerUri)
    )

    val hubClient = new NotificationHubClient(conf.notificationHubConnection, ws)
    hubClient.submitNotificationHubJob(notificationJob).map {
      case \/-(job) => Logger.info(s"Success, job created with id ${job.jobId}\n$job")
      case -\/(failure) => Logger.error(failure.toString)
    }
  }

  private def cleanup(container: CloudBlobContainer): Unit = {

    def listAllFiles(directory: CloudBlobDirectory): List[CloudBlob] = {
      // toList to force the lazy list to be evaluated
      val directoryContent = directory.listBlobs().toList
      directoryContent.flatMap {
        case blob: CloudBlob => List(blob)
        case dir: CloudBlobDirectory => listAllFiles(dir)
        case _ => Nil
      }
    }

    val tooOld = DateTime.now().minusDays(conf.backupRetention).toDate
    val directory = container.getDirectoryReference(conf.directoryName)
    val allBlobs = listAllFiles(directory)
    val blobsToDelete = allBlobs.filter(_.getProperties.getLastModified.before(tooOld))
    blobsToDelete.foreach { blob =>
      blob.deleteIfExists()
      Logger.info(s"deleted old backup: ${blob.getName}")
    }
  }
}
