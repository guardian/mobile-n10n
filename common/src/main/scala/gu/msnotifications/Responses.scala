package gu.msnotifications

import gu.msnotifications.HubFailure.{HubParseFailed, HubServiceError}
import gu.msnotifications.NotificationHubClient.HubResult
import models.WindowsMobile
import notifications.providers.{RegistrationResponse => RegistrarResponse, UserIdNotInTags}
import org.joda.time.DateTime
import play.api.libs.ws.WSResponse

import scala.util.Try
import scalaz.\/
import scalaz.std.option.optionSyntax._

trait XmlReads[T] {
  def reads(xml: scala.xml.Elem): HubResult[T]
}

object XmlParser {
  private def getXml(response: WSResponse): HubResult[scala.xml.Elem] = {
    if (response.status >= 200 || response.status < 300)
      Try(response.xml).toOption \/> HubParseFailed.invalidXml(response.body)
    else
      \/.left(parseError(response))
  }

  def parseError(response: WSResponse): HubFailure = {
    Try(response.xml).toOption.flatMap {
      HubServiceError.fromXml
    } getOrElse {
      HubServiceError.fromWSResponse(response)
    }
  }

  def parse[T](response: WSResponse)(implicit reader: XmlReads[T]): HubResult[T] =
    getXml(response) flatMap reader.reads
}

object Responses {

  implicit class RichXmlElem(xml: scala.xml.Elem) {

    def getStrings(s: String): Seq[String] = (xml \ s).map(_.text)

    def getString(s: String): HubResult[String] =
      getStrings(s).headOption \/> HubParseFailed(body = xml.toString(), reason = s"Missing field $s")

    def getDateTime(s: String): HubResult[DateTime] = {
      getString(s).flatMap { dateTime =>
        Try(DateTime.parse(dateTime)).toOption \/> HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$dateTime' in field $s as datetime")
      }
    }
  }
}

object RegistrationResponse {
  import Responses._

  implicit val reader = new XmlReads[RegistrationResponse] {
    def reads(xml: scala.xml.Elem) = for {
        expirationTime <- xml.getDateTime("ExpirationTime")
        registrationId <- xml.getString("RegistrationId").map(WNSRegistrationId.apply)
        channelUri <- xml.getString("ChannelUri")
        tags = xml.getStrings("Tags").flatMap(_.split(",").map(_.stripPrefix(" ")))
    } yield RegistrationResponse(registrationId, tags.toList, channelUri, expirationTime)
  }
}
case class RegistrationResponse(registration: WNSRegistrationId, tags: List[String], channelUri: String, expirationTime: DateTime) {
  def toRegistrarResponse: UserIdNotInTags \/ RegistrarResponse = {
    val tagsFromUris = Tags.fromUris(tags.toSet)
    for {
      userId <- tagsFromUris.findUserId \/> UserIdNotInTags()
    } yield RegistrarResponse(
      deviceId = channelUri,
      WindowsMobile,
      userId = userId,
      topics = tagsFromUris.decodedTopics
    )
  }
}

object AtomFeedResponse {
  import Responses._

  implicit def reader[T](implicit reader: XmlReads[T]): XmlReads[AtomFeedResponse[T]] = new XmlReads[AtomFeedResponse[T]] {
    def reads(xml: scala.xml.Elem) =  for {
      title <- xml.getString("title")
      items <- getItems(xml)(reader)
    } yield AtomFeedResponse(title, items)
  }

  private def getItems[T](xml: scala.xml.Elem)(implicit reader: XmlReads[T]) = {
    val results = (xml \ "entry").collect {
      case elem: scala.xml.Elem => reader.reads(elem)
    }
    val (left, right) = results.partition(_.isLeft)
    val errors = left.flatMap(_.swap.toOption)
    val successes = right.flatMap(_.toOption)
    if (errors.nonEmpty)
      \/.left(errors.head)
    else
      \/.right(successes.toList)
  }
}

case class AtomFeedResponse[T](title: String, items: List[T])