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

  def register(registration: MobileRegistration): Future[HubResult[WNSRegistrationId]] = {
    register(RawWindowsRegistration.fromMobileRegistration(registration))
  }

  def register(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[WNSRegistrationId]] = {
    request(s"$notificationsHubUrl/registrations/?api-version=2015-01")
      .post(rawWindowsRegistration.toXml)
      .map(processRegistrationResponse)
  }

  def update(registrationId: WNSRegistrationId,
             rawWindowsRegistration: RawWindowsRegistration
              ): Future[HubResult[WNSRegistrationId]] = {
    request(s"$notificationsHubUrl/registration/${registrationId.registrationId}?api-version=2015-01")
      .post(rawWindowsRegistration.toXml)
      .map(processRegistrationResponse)
  }

  def sendPush(azureWindowsPush: AzureXmlPush): Future[HubResult[Unit]] = {
    val serviceBusTags = azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList

    request(s"$notificationsHubUrl/messages/?api-version=2015-01")
      .withHeaders("X-WNS-Type" -> azureWindowsPush.wnsType)
      .withHeaders("ServiceBusNotification-Format" -> "windows")
      .withHeaders(serviceBusTags: _*)
      .post(azureWindowsPush.xml)
      .map(processResponse).map(_.map(_ => ()))
  }

  /**
   * This is used for health-checking only: return the title of the feed,
   * which is expected to be 'Registrations'.
   * @return the title of the feed if it succeeds getting one
   */
  def fetchRegistrationsListEndpoint: Future[HubResult[String]] = {
    request(s"$notificationsHubUrl/registrations/?api-version=2015-01")
      .get()
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

  private def processRegistrationResponse(response: WSResponse): HubResult[WNSRegistrationId] = {
    processResponse(response).flatMap { xml =>
      val parser = RegistrationResponseParser(xml)
      parser.registrationId.toRightDisjunction {
        HubServiceError.fromXml(xml) getOrElse HubParseFailed(body = response.body, reason = "Received in valid XML")
      }
    }
  }

  private case class RegistrationResponseParser(xml: scala.xml.Elem) {

    def registrationId: Option[WNSRegistrationId] = {
      (xml \\ "RegistrationId").map(_.text).headOption.map(WNSRegistrationId.apply)
    }
  }

}