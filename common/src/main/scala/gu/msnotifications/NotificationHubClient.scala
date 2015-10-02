package gu.msnotifications

import models.{Registration, Push}
import notifications.providers.{NotificationRegistrar, NotificationSender, RegistrationResponse => RegistrarResponse}
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
    (implicit executionContext: ExecutionContext)
  extends NotificationSender with NotificationRegistrar {

  import notificationHubConnection._
  import NotificationHubClient.HubResult

  val name = "WNS"

  override def register(registration: Registration): Future[HubResult[RegistrarResponse]] =
    register(RawWindowsRegistration.fromMobileRegistration(registration)).map { hubResult =>
      hubResult.map { _.toRegistrarResponse }
    }

  override def sendNotification(push: Push): Future[HubResult[Unit]] = sendNotification(AzureRawPush.fromPush(push))

  private[this] def register(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationResponse]] = {
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


  private[this] def sendNotification(azureWindowsPush: AzureRawPush): Future[HubResult[Unit]] = {
    val serviceBusTags = azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList

    request(s"/messages/")
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