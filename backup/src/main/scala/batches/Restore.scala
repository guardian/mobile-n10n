package batches

import java.text.SimpleDateFormat
import java.util
import javax.inject.Inject

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions._
import com.microsoft.azure.storage.blob._
import gu.msnotifications.{NotificationHubJobType, _}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.ws.WSClient
import collection.JavaConversions._

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn
import scalaz.{-\/, \/-}

class Restore @Inject() (conf: BackupConfiguration, ws: WSClient)(implicit ec: ExecutionContext) extends Batch {

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")

  def execute(): Future[Unit] = {
    val storageAccount = CloudStorageAccount.parse(conf.storageConnectionString)
    val blobClient = storageAccount.createCloudBlobClient()
    val container = blobClient.getContainerReference(conf.containerName)
    val directory = container.getDirectoryReference(conf.directoryName)
    val blobs = AzureUtils.listAllFiles(directory).
      filter(_.getName.endsWith("Output.txt")).
      sortBy(_.getProperties.getLastModified)

    blobs.zipWithIndex.foreach { case (blob, index) =>
      val formattedDate = dateFormat.format(blob.getProperties.getLastModified)
      val formattedSize = blob.getProperties.getLength / 1024 / 1024
      println(s"[$index] $formattedDate (${formattedSize}MB)")
    }

    printf("Which backup would you like to restore? ")
    val inputIndex = StdIn.readInt()
    val selectedBlob = blobs(inputIndex)

    println("You are about to restore the registration database with the following settings")
    println(s"\tbackup date = ${dateFormat.format(selectedBlob.getProperties.getLastModified)}")
    println(s"\tbackup size = ${selectedBlob.getProperties.getLength / 1024 / 1024}")
    println(s"\tendpoint = ${conf.notificationHubConnection.notificationsHubUrl}")
    println("Are you sure you want to proceed? (yes/no) ")
    val positive = StdIn.readLine().toLowerCase == "yes"

    if (positive) {
      restore(container, selectedBlob)
    } else {
      Future.successful(println("Nothing to do, terminating."))
    }
  }


  def restore(container: CloudBlobContainer, selectedBlob: CloudBlob): Future[Unit] = {
    val sharedAccessPolicy = new SharedAccessBlobPolicy()
    sharedAccessPolicy.setPermissions(util.EnumSet.of(READ, LIST))
    sharedAccessPolicy.setSharedAccessExpiryTime(DateTime.now(DateTimeZone.UTC).plusHours(3).toDate)
    val arg = selectedBlob.generateSharedAccessSignature(sharedAccessPolicy, "")
    val blobUri = container.getUri + "?" + arg

    val notificationJob = NotificationHubJobRequest(
      jobType = NotificationHubJobType.ImportCreateRegistrations,
      importFileUri = Some(blobUri)
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
}
