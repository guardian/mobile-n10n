package gu.msnotifications

import gu.msnotifications.HubFailure.{HubParseFailed, HubServiceError}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scalaz.\/
import scalaz.std.option.optionSyntax._

case class NotificationHubError(message: String)

object NotificationHubClient {
  type HubResult[T] = HubFailure \/ T
}
/**
 * https://msdn.microsoft.com/en-us/library/azure/dn223264.aspx
 */
final class NotificationHubClient(notificationHubConnection: NotificationHubConnection, wsClient: WSClient)
                                 (implicit executionContext: ExecutionContext) {

  import notificationHubConnection._
  import NotificationHubClient.HubResult

  def post(uri: String, data: scala.xml.Elem, headers: List[(String, String)] = Nil) = {
    wsClient
      .url(uri)
      .withHeaders("Authorization" -> authorizationHeader(uri))
      .withHeaders(headers: _*)
      .post(data)
  }

  def get(uri: String, headers: List[(String, String)] = Nil) = {
    wsClient
      .url(uri)
      .withHeaders("Authorization" -> authorizationHeader(uri))
      .withHeaders(headers: _*)
      .get()
  }

  def register(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationId]] = {
    post(
      uri = s"""$notificationsHubUrl/registrations/?api-version=2015-01""",
      data = rawWindowsRegistration.toXml
    ).map(processRegistrationResponse)
  }

  def update(registrationId: RegistrationId,
             rawWindowsRegistration: RawWindowsRegistration
              ): Future[HubResult[RegistrationId]] = {
    post(
      uri = s"""$notificationsHubUrl/registration/${registrationId.registrationId}?api-version=2015-01""",
      data = rawWindowsRegistration.toXml
    ).map(processRegistrationResponse)
  }

  def sendPush(azureWindowsPush: AzureXmlPush): Future[HubResult[Unit]] = {
    val serviceBusTags = azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList

    post(
      uri = s"""$notificationsHubUrl/messages/?api-version=2015-01""",
      data = azureWindowsPush.xml,
      headers = ("X-WNS-Type" -> azureWindowsPush.wnsType) ::
        ("ServiceBusNotification-Format" -> "windows") ::
        serviceBusTags
    ).map(processResponse).map(_.map(_ => ()))
  }

  /**
   * This is used for health-checking only: return the title of the feed,
   * which is expected to be 'Registrations'.
   * @return the title of the feed if it succeeds getting one
   */
  def fetchRegistrationsListEndpoint: Future[HubResult[String]] = {
    get(s"""$notificationsHubUrl/registrations/?api-version=2015-01""")
      .map(processResponse)
      .map(_.map(xml => (xml \ "title").text))
  }

  private def processResponse(response: WSResponse): HubResult[scala.xml.Elem] = {
    Try(response.xml).toOption.toRightDisjunction {
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

  private def processRegistrationResponse(response: WSResponse): HubResult[RegistrationId] = {
    processResponse(response).flatMap { xml =>
      val parser = RegistrationResponseParser(xml)
      parser.registrationId.toRightDisjunction {
        HubServiceError.fromXml(xml) getOrElse HubParseFailed(body = response.body, reason = "Received in valid XML")
      }
    }
  }

  private case class RegistrationResponseParser(xml: scala.xml.Elem) {

    def registrationId: Option[RegistrationId] = {
      (xml \\ "RegistrationId").map(_.text).headOption.map(RegistrationId.apply)
    }

  }

}