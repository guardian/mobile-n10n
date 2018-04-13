package azure

import HubFailure.{HubInvalidResponse, HubParseFailed, HubServiceError}
import NotificationHubClient.HubResult
import org.joda.time.DateTime
import play.api.libs.ws.WSResponse
import scala.util.{Failure, Success, Try}
import scala.xml.Elem
import models.{Android, Platform, iOS}
import utils.WSImplicits._
import cats.syntax.either._

trait XmlReads[T] {
  def reads(xml: Elem): HubResult[T]
}

object XmlParser {
  private def getXml(response: WSResponse): HubResult[Elem] = {
    if (response.isSuccess)
      Either.fromOption(Try(response.xml).toOption, HubParseFailed.invalidXml(response.body))
    else
      Left(parseError(response))
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
      Either.fromOption(textNodes(s).headOption, HubParseFailed(body = xml.toString(), reason = s"Missing field $s"))

    def textNodeOption(s: String): HubResult[Option[String]] =
      Right(textNodes(s).headOption)

    def dateTimeNode(s: String): HubResult[DateTime] = {
      textNode(s).flatMap { dateTime =>
        Either.fromOption(
          Try(DateTime.parse(dateTime)).toOption,
          HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$dateTime' in field $s as datetime")
        )
      }
    }

    def dateTimeNodeOption(s: String): HubResult[Option[DateTime]] = {
      textNodeOption(s) flatMap {
        case Some(dateTimeValue) => Try(DateTime.parse(dateTimeValue)) match {
          case Success(dateTime) => Right(Some(dateTime))
          case Failure(_) => Left(
            HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$dateTimeValue' in field $s as datetime")
          )
        }
        case None => Right(None)
      }
    }

    def integerNode(s: String): HubResult[Int] = {
      textNode(s).flatMap { integer =>
        Either.fromOption(Try(integer.toInt).toOption, HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$integer' in field $s as an integer"))
      }
    }

    def doubleNode(s: String): HubResult[Double] = {
      textNode(s).flatMap { double =>
        Either.fromOption(Try(double.toDouble).toOption, HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$double' in field $s as a double"))
      }
    }

    def doubleNodeOption(s: String): HubResult[Option[Double]] = {
      textNodeOption(s).flatMap {
        case Some(doubleValue) => Try(doubleValue.toDouble) match {
          case Success(double) => Right(Some(double))
          case Failure(_) => Left(HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$doubleValue' in field $s as a double"))
        }
        case None => Right(None)
      }
    }
  }
}

object RegistrationResponse {
  import Responses._

  implicit val reader = new XmlReads[RegistrationResponse] {
    def reads(xml: Elem) = {
      xml.label match {
        case "GcmRegistrationDescription" =>
          for {
            expirationTime <- xml.dateTimeNode("ExpirationTime")
            registrationId <- xml.textNode("RegistrationId").map(NotificationHubRegistrationId.apply)
            gcmRegistrationId <- xml.textNode("GcmRegistrationId")
            tags = xml.textNodes("Tags").flatMap(_.split(",").map(_.stripPrefix(" ")))
          } yield GCMRegistrationResponse(registrationId, tags.toList, gcmRegistrationId, expirationTime)
        case "AppleRegistrationDescription" =>
          for {
            expirationTime <- xml.dateTimeNode("ExpirationTime")
            registrationId <- xml.textNode("RegistrationId").map(NotificationHubRegistrationId.apply)
            deviceToken <- xml.textNode("DeviceToken")
            tags = xml.textNodes("Tags").flatMap(_.split(",").map(_.stripPrefix(" ")))
          } yield APNSRegistrationResponse(registrationId, tags.toList, deviceToken, expirationTime)
      }
    }
  }
}
sealed trait RegistrationResponse {
  def tags: List[String]
  def registration: NotificationHubRegistrationId
  def expirationTime: DateTime
  def tagsAsSet: Set[String]
  def platform: Platform
  def deviceId: String
}

case class GCMRegistrationResponse(
  registration: NotificationHubRegistrationId,
  tags: List[String],
  gcmRegistrationId: String,
  expirationTime: DateTime) extends RegistrationResponse {
  lazy val tagsAsSet = tags.toSet
  override def platform: Platform = Android
  override def deviceId: String = gcmRegistrationId
}

case class APNSRegistrationResponse(
  registration: NotificationHubRegistrationId,
  tags: List[String],
  deviceToken: String,
  expirationTime: DateTime) extends RegistrationResponse {
  lazy val tagsAsSet = tags.toSet
  override def platform: Platform = iOS
  override def deviceId: String = deviceToken
}


object AtomEntry {
  import Responses._

  implicit def reader[T](implicit reader: XmlReads[T]): XmlReads[AtomEntry[T]] = new XmlReads[AtomEntry[T]] {
    def reads(xml: Elem) =  for {
      title <- xml.textNode("title")
      item <- getItem(xml)(reader)
    } yield AtomEntry(title, item)
  }

  private def getItem[T](xml: Elem)(implicit reader: XmlReads[T]): HubResult[T] = {
    val results = (xml \ "content").flatMap(_.child).collectFirst {
      case elem: Elem => reader.reads(elem)
    }
    results.getOrElse(Left(HubFailure.HubParseFailed(xml.toString(), "No Content in the xml")))
  }
}

case class AtomEntry[T](title: String, content: T)

object AtomFeedResponse {
  import Responses._

  implicit def reader[T](implicit reader: XmlReads[T]): XmlReads[AtomFeedResponse[T]] = new XmlReads[AtomFeedResponse[T]] {
    def reads(xml: Elem) =  for {
      title <- xml.textNode("title")
      items <- getItems(xml)(reader)
    } yield AtomFeedResponse(title, items)
  }

  private def getItems[T](xml: Elem)(implicit reader: XmlReads[T]): HubResult[List[AtomEntry[T]]] = {
    val results = (xml \ "entry").collect {
      case elem: scala.xml.Elem => AtomEntry.reader(reader).reads(elem)
    }
    val (left, right) = results.partition(_.isLeft)
    val errors = left.flatMap(_.swap.toOption)
    val successes = right.flatMap(_.toOption)
    if (errors.nonEmpty)
      Left(errors.head)
    else
      Right(successes.toList)
  }
}

case class AtomFeedResponse[T](title: String, entries: List[AtomEntry[T]]) {
  def items: List[T] = entries.map(_.content)
}