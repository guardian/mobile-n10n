package gu.msnotifications

import gu.msnotifications.HubFailure.{HubParseFailed, HubServiceError}
import models.MobileRegistration
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

  private def request(uri: String) = wsClient
    .url(uri)
    .withHeaders("Authorization" -> authorizationHeader(uri))

  def register(registration: MobileRegistration): Future[HubResult[RegistrationResponse]] = {
    register(RawWindowsRegistration.fromMobileRegistration(registration))
  }

  def register(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationResponse]] = {
    request(s"$notificationsHubUrl/registrations/?api-version=2015-01")
      .post(rawWindowsRegistration.toXml)
      .map(RegistrationResponse.fromWSResponse)
  }

  def update(registrationId: WNSRegistrationId,
             rawWindowsRegistration: RawWindowsRegistration
              ): Future[HubResult[RegistrationResponse]] = {
    request(s"$notificationsHubUrl/registration/${registrationId.registrationId}?api-version=2015-01")
      .post(rawWindowsRegistration.toXml)
      .map(RegistrationResponse.fromWSResponse)
  }

  def sendPush(azureWindowsPush: AzureXmlPush): Future[HubResult[Unit]] = {
    val serviceBusTags = azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList

    request(s"$notificationsHubUrl/messages/?api-version=2015-01")
      .withHeaders("X-WNS-Type" -> azureWindowsPush.wnsType)
      .withHeaders("ServiceBusNotification-Format" -> "windows")
      .withHeaders(serviceBusTags: _*)
      .post(azureWindowsPush.xml)
      .map(XmlResponse.fromWSResponse).map(_.map(_ => ()))
  }

  /**
   * This is used for health-checking only: return the title of the feed,
   * which is expected to be 'Registrations'.
   * @return the title of the feed if it succeeds getting one
   */
  def fetchRegistrationsListEndpoint: Future[HubResult[String]] = {
    request(s"$notificationsHubUrl/registrations/?api-version=2015-01")
      .get()
      .map(XmlResponse.fromWSResponse)
      .map(_.map(xml => (xml.xml \ "title").text))
  }

}