package gu.msnotifications

import models.MobileRegistration
import play.api.libs.ws.{WSClient}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

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

  private def request(path: String) = {
    val uri = s"$notificationsHubUrl$path?api-version=2015-01"
    wsClient
      .url(uri)
      .withHeaders("Authorization" -> authorizationHeader(uri))
  }

  def register(registration: MobileRegistration): Future[HubResult[RegistrationResponse]] = {
    register(RawWindowsRegistration.fromMobileRegistration(registration))
  }

  def register(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationResponse]] = {
    request("/registrations/")
      .post(rawWindowsRegistration.toXml)
      .map(XmlParser.parse[RegistrationResponse])
  }

  def update(registrationId: WNSRegistrationId,
             rawWindowsRegistration: RawWindowsRegistration
              ): Future[HubResult[RegistrationResponse]] = {
    request(s"/registration/${registrationId.registrationId}")
      .post(rawWindowsRegistration.toXml)
      .map(XmlParser.parse[RegistrationResponse])
  }

  def sendNotification(azureWindowsPush: AzureXmlPush): Future[HubResult[Unit]] = {
    val serviceBusTags = azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList

    request(s"/messages/")
      .withHeaders("X-WNS-Type" -> azureWindowsPush.wnsType)
      .withHeaders("ServiceBusNotification-Format" -> "windows")
      .withHeaders(serviceBusTags: _*)
      .post(azureWindowsPush.xml)
      .map { response =>
        if (response.status == 200)
          \/.right(())
        else
          \/.left(XmlParser.parseError(response))
      }
  }

  /**
   * This is used for health-checking only: return the title of the feed,
   * which is expected to be 'Registrations'.
   * @return the title of the feed if it succeeds getting one
   */
  def fetchRegistrationsListEndpoint: Future[HubResult[String]] = {
    request(s"/registrations/")
      .get()
      .map(XmlParser.parse[AtomFeedResponse[RegistrationResponse]])
      .map(_.map(_.title))
  }

  def registrationsByTag(tag: String) = {
    request(s"/tags/$tag/registrations")
      .get()
      .map(XmlParser.parse[AtomFeedResponse[RegistrationResponse]])
  }


}