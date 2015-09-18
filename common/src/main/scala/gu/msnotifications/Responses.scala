package gu.msnotifications

import gu.msnotifications.HubFailure.{HubParseFailed, HubServiceError}
import org.joda.time.DateTime
import play.api.libs.ws.WSResponse

import scala.util.Try

import scalaz.std.option.optionSyntax._

import NotificationHubClient.HubResult

object XmlResponse {
  def fromWSResponse(response: WSResponse): HubResult[XmlResponse] = {
    Try(response.xml).toOption.map(XmlResponse.apply) \/> {
      if (response.status != 200)
        HubServiceError(
          reason = response.statusText,
          code = response.status
        )
      else
        HubParseFailed(
          body = response.body,
          reason = "Failed to find any XML"
        )
    }
  }
}

case class XmlResponse(xml: scala.xml.Elem) {

  def getStrings(s: String): Seq[String] = (xml \\ s).map(_.text)

  def getString(s: String): HubResult[String] = getStrings(s).headOption \/> HubParseFailed(body = xml.toString(), reason = s"Missing field $s")

  def getDateTime(s: String): HubResult[DateTime] = {
    getString(s).flatMap { dateTime =>
      Try(DateTime.parse(dateTime)).toOption \/> HubParseFailed(body = xml.toString(), reason = s"Failed to parse '$dateTime' in field $s as datetime")
    }
  }
}

object RegistrationResponse {
  def fromWSResponse(response: WSResponse) = XmlResponse.fromWSResponse(response).flatMap(fromXmlResponse)

  def fromXmlResponse(response: XmlResponse): HubResult[RegistrationResponse] = {
    for {
      expirationTime <- response.getDateTime("ExpirationTime")
      registrationId <- response.getString("RegistrationId").map(WNSRegistrationId.apply)
      channelUri <- response.getString("ChannelUri")
      tags = response.getStrings("Tags").flatMap(_.split(",").map(_.stripPrefix(" ")))
    } yield RegistrationResponse(registrationId, tags.toList, channelUri, expirationTime)
  }
}
case class RegistrationResponse(registration: WNSRegistrationId, tags: List[String], channelUri: String, expirationTime: DateTime)