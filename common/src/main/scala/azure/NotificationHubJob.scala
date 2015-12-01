package azure

import NotificationHubClient._
import org.joda.time.DateTime
import NotificationHubJobType._
import Responses.RichXmlElem
import play.api.Logger

import scala.xml.{NodeSeq, Elem}
import scalaz.\/

case class NotificationHubJobRequest(
  jobType: NotificationHubJobType,
  outputContainerUri: Option[String] = None,
  importFileUri: Option[String] = None
) {

  private def xmlOutputContainerUri: NodeSeq = outputContainerUri.map(uri => <OutputContainerUri>{uri}</OutputContainerUri>).getOrElse(NodeSeq.Empty)
  private def xmlImportFileUri: NodeSeq = importFileUri.map(uri => <ImportFileUri>{uri}</ImportFileUri>).getOrElse(NodeSeq.Empty)

  def toXml: Elem = {
    <entry xmlns="http://www.w3.org/2005/Atom">
      <content type="application/atom+xml;type=entry;charset=utf-8">
        <NotificationHubJob xmlns:i="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://schemas.microsoft.com/netservices/2010/10/servicebus/connect">
          <Type>{jobType}</Type>
          {xmlOutputContainerUri}
          {xmlImportFileUri}
        </NotificationHubJob>
      </content>
    </entry>
  }
}

case class NotificationHubJob(
  jobId: String,
  progress: Option[Double],
  jobType: NotificationHubJobType,
  jobStatus: Option[String],
  outputContainerUri: String,
  importFileUri: Option[String],
  failure: Option[String],
  outputProperties: Map[String, String],
  createdAt: Option[DateTime],
  updatedAt: Option[DateTime]
)

object NotificationHubJob {
  val logger = Logger(classOf[NotificationHubJob])

  private def parseProperties(seq: NodeSeq): HubResult[Map[String, String]] = {
    \/.right(seq.map { elem =>
      (elem \ "Key" text) -> (elem \ "Value" text)
    }.toMap)
  }

  implicit val reads = new XmlReads[NotificationHubJob] {
    override def reads(xml: Elem): HubResult[NotificationHubJob] = {
      logger.debug(s"Reading NotificatioHubJob from XML: $xml")

      for {
        jobId <- xml.textNode("JobId")
        progress <- xml.doubleNodeOption("Progress")
        jobType <- xml.textNode("Type")
        jobStatus <- xml.textNodeOption("Status")
        outputContainerUri <- xml.textNode("OutputContainerUri")
        importFileUri <- xml.textNodeOption("ImportFileUri")
        failure <- xml.textNodeOption("Failure")
        outputProperties <- parseProperties(xml \ "OutputProperties")
        createdAt <- xml.dateTimeNodeOption("CreatedAt")
        updatedAt <- xml.dateTimeNodeOption("updatedAt")
      } yield NotificationHubJob(
        jobId = jobId,
        progress = progress,
        jobType = NotificationHubJobType.withName(jobType),
        jobStatus = jobStatus,
        outputContainerUri = outputContainerUri,
        importFileUri = importFileUri,
        failure = failure,
        outputProperties = outputProperties,
        createdAt = createdAt,
        updatedAt = updatedAt
      )
    }
  }
}
