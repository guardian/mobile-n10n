package gu.msnotifications

import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

case class NotificationHubError(message: String)

object NotificationHubClient {
  type HubResult[T] = HubFailure \/ T
}
/**
 * https://msdn.microsoft.com/en-us/library/azure/dn223264.aspx
 */
final class NotificationHubClient(
  notificationHubConnection: NotificationHubConnection, wsClient: WSClient)
    (implicit executionContext: ExecutionContext) {

  import NotificationHubClient.HubResult
  import notificationHubConnection._

  private object Endpoints {
    val Registrations = "/registrations/"
    val Messages = "/messages/"
  }

  def create(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationResponse]] = {
    request(Endpoints.Registrations)
      .post(rawWindowsRegistration.toXml)
      .map(XmlParser.parse[RegistrationResponse])
  }

  def update(registrationId: WNSRegistrationId,
             rawWindowsRegistration: RawWindowsRegistration
              ): Future[HubResult[RegistrationResponse]] = {
    request(s"${Endpoints.Registrations}${registrationId.registrationId}")
      .put(rawWindowsRegistration.toXml)
      .map(XmlParser.parse[RegistrationResponse])
  }

  def sendNotification(azureWindowsPush: AzureRawPush): Future[HubResult[Unit]] = {
    val serviceBusTags = azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList

    request(Endpoints.Messages)
      .withHeaders("X-WNS-Type" -> azureWindowsPush.wnsType)
      .withHeaders("ServiceBusNotification-Format" -> "windows")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .withHeaders(serviceBusTags: _*)
      .post(azureWindowsPush.body)
      .map { response =>
        if (response.status == 200)
          \/.right(())
        else
          \/.left(XmlParser.parseError(response))
      }
  }

  private def request(path: String) = {
    val uri = s"$notificationsHubUrl$path?api-version=2015-01"
    wsClient
      .url(uri)
      .withHeaders("Authorization" -> authorizationHeader(uri))
  }
  /**
   * This is used for health-checking only: return the title of the feed,
   * which is expected to be 'Registrations'.
   * @return the title of the feed if it succeeds getting one
   */
  def fetchRegistrationsListEndpoint: Future[HubResult[String]] = {
    request(Endpoints.Registrations)
      .get()
      .map(XmlParser.parse[AtomFeedResponse[RegistrationResponse]])
      .map(_.map(_.title))
  }

  def registrationsByTag(tag: String) = {
    request(s"/tags/$tag/registrations")
      .get()
      .map(XmlParser.parse[AtomFeedResponse[RegistrationResponse]])
  }

  def registrationsByChannelUri(channelUri: String): Future[HubResult[List[RegistrationResponse]]] = {
    request(Endpoints.Registrations)
      .get()
      .map(XmlParser.parse[AtomFeedResponse[RegistrationResponse]])
      .map { hubResult => hubResult.map(_.items) }
  }

}