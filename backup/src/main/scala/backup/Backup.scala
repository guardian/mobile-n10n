package backup

import java.util
import javax.inject.Inject

import azure.{HubFailure, NotificationHubClient, NotificationHubJobRequest, NotificationHubJobType}
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions._
import com.microsoft.azure.storage.blob._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.ws.WSClient
import play.api.Logger
import collection.JavaConverters._

import scala.concurrent.{ExecutionContext, Future}

class Backup(conf: Configuration, ws: WSClient)(implicit ec: ExecutionContext) extends Batch {
  val logger = Logger(classOf[Backup])

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
      case Right(job) =>
        logger.info(s"Job successfully created with id ${job.jobId}")
        logger.debug(s"Job submitted to hub: $job")
      case Left(failure: HubFailure) =>
        logger.error(s"Failed submitting job to hub (provider ${failure.providerName}, reason: ${failure.reason})")
    }
  }

  private def cleanup(container: CloudBlobContainer): Unit = {

    def listAllFiles(directory: CloudBlobDirectory): List[CloudBlob] = {
      // toList to force the lazy list to be evaluated
      val directoryContent = directory.listBlobs().asScala.toList
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
      logger.info(s"deleted old backup: ${blob.getName}")
    }
  }
}
