package gu.msnotifications

import gu.msnotifications.HubFailure.{HubParseFailed, HubServiceError}
import gu.msnotifications.NotificationHubClient.HubResult
import models.WindowsMobile
import notifications.providers.{RegistrationResponse => RegistrarResponse, UserIdNotInTags}
import org.joda.time.DateTime
import play.api.libs.ws.WSResponse
import scala.util.{Failure, Success, Try}
import scala.xml.Elem
import scalaz.{-\/, \/-, \/}
import scalaz.syntax.either._
import scalaz.std.option.optionSyntax._

trait XmlReads[T] {
  def reads(xml: Elem): HubResult[T]
}

object XmlParser {
  private def getXml(response: WSResponse): HubResult[Elem] = {
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

  implicit class RichXmlElem(xml: Elem) {

    def textNodes(s: String): Seq[String] = (xml \ s).map(_.text)

    def textNode(s: String): HubResult[String] =
      textNodes(s).headOption \/> HubParseFailed(body = xml.toString(), reason = s"Missing field $s")

    def textNodeOption(s: String): HubResult[Option[String]] =
      textNodes(s).headOption.right

    def dateTimeNode(s: String): HubResult[DateTime] = {
      textNode(s).flatMap { dateTime =>
        Try(DateTime.parse(dateTime)).toOption \/> HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$dateTime' in field $s as datetime")
      }
    }

    def dateTimeNodeOption(s: String): HubResult[Option[DateTime]] = {
      textNodeOption(s) flatMap {
        case Some(dateTimeValue) => Try(DateTime.parse(dateTimeValue)) match {
          case Success(dateTime) => Some(dateTime).right
          case Failure(_) => HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$dateTimeValue' in field $s as datetime").left
        }
        case None => None.right
      }
    }

    def doubleNode(s: String): HubResult[Double] = {
      textNode(s).flatMap { double =>
        Try(double.toDouble).toOption \/> HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$double' in field $s as a double")
      }
    }

    def doubleNodeOption(s: String): HubResult[Option[Double]] = {
      textNodeOption(s).flatMap {
        case Some(doubleValue) => Try(doubleValue.toDouble) match {
          case Success(double) => \/-(Some(double))
          case Failure(_) => -\/(HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$doubleValue' in field $s as a double"))
        }
        case None => \/-(None)
      }
    }
  }
}

object RegistrationResponse {
  import Responses._

  implicit val reader = new XmlReads[RegistrationResponse] {
    def reads(xml: Elem) = for {
        expirationTime <- xml.dateTimeNode("ExpirationTime")
        registrationId <- xml.textNode("RegistrationId").map(WNSRegistrationId.apply)
        channelUri <- xml.textNode("ChannelUri")
        tags = xml.textNodes("Tags").flatMap(_.split(",").map(_.stripPrefix(" ")))
    } yield RegistrationResponse(registrationId, tags.toList, channelUri, expirationTime)
  }
}
case class RegistrationResponse(registration: WNSRegistrationId, tags: List[String], channelUri: String, expirationTime: DateTime) {
  def toRegistrarResponse: UserIdNotInTags \/ RegistrarResponse = {
    val tagsFromUris = Tags.fromStrings(tags.toSet)
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
    def reads(xml: Elem) =  for {
      title <- xml.textNode("title")
      items <- getItems(xml)(reader)
    } yield AtomFeedResponse(title, items)
  }

  private def getItems[T](xml: Elem)(implicit reader: XmlReads[T]) = {
    val results = (xml \ "content").flatMap(_.child).map {
      case elem: Elem => reader.reads(elem)
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