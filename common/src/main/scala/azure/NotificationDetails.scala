package azure

import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

import scala.xml.Elem
import cats.syntax.either._

case class NotificationDetails(
  state: NotificationState,
  enqueueTime: DateTime,
  startTime: Option[DateTime],
  endTime: Option[DateTime],
  notificationBody: String,
  targetPlatforms: List[TargetPlatform],
  wnsOutcomeCounts: Option[List[Outcome]],
  apnsOutcomeCounts: Option[List[Outcome]],
  gcmOutcomeCounts: Option[List[Outcome]],
  tags: String,
  pnsErrorDetailsUri: Option[String]
)

object NotificationDetails {

  import Responses._

  implicit val jf = Json.format[NotificationDetails]

  implicit val reader = new XmlReads[NotificationDetails] {
    def reads(xml: Elem) = {
      for {
        state <- xml.textNode("State").flatMap(NotificationState.fromString)
        enqueueTime <- xml.dateTimeNode("EnqueueTime")
        startTime <- xml.dateTimeNodeOption("StartTime")
        endTime <- xml.dateTimeNodeOption("EndTime")
        notificationBody <- xml.textNode("NotificationBody")
        targetPlatforms <- xml.textNode("TargetPlatforms").map(targetPlatformsFromCSV)
        tags <- xml.textNode("Tags")
        pnsErrorDetailsUri <- xml.textNodeOption("PnsErrorDetailsUri")
      } yield NotificationDetails(
        state = state,
        enqueueTime = enqueueTime,
        startTime = startTime,
        endTime = endTime,
        notificationBody = notificationBody,
        targetPlatforms = targetPlatforms,
        wnsOutcomeCounts = getCounts(xml, "WnsOutcomeCounts"),
        apnsOutcomeCounts = getCounts(xml, "ApnsOutcomeCounts"),
        gcmOutcomeCounts = getCounts(xml, "GcmOutcomeCounts"),
        tags = tags,
        pnsErrorDetailsUri = pnsErrorDetailsUri
      )
    }

    private def targetPlatformsFromCSV(csv: String): List[TargetPlatform] = {
      csv.split(',').toList.flatMap(TargetPlatform.fromString)
    }

    private def getCounts(xml: Elem, name: String): Option[List[Outcome]] = {
      getElem(xml, name).map { nameElem =>
        (nameElem \ "Outcome").collect {
          case outComeElem: Elem => Outcome.reader.reads(outComeElem).toOption
        }.flatten.toList
      }
    }

    private def getElem(xml: Elem, nodeName: String): Option[Elem] =
      (xml \ nodeName).collectFirst { case elem: Elem => elem }

  }
}