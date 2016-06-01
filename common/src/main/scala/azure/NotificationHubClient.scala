package azure

import play.api.Logger
import play.api.http.Status
import play.api.libs.ws.{WSResponse, WSClient}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/
import scalaz.syntax.either._

case class NotificationHubError(message: String)

object NotificationHubClient {
  type HubResult[T] = HubFailure \/ T
}
/**
 * https://msdn.microsoft.com/en-us/library/azure/dn223264.aspx
 */
class NotificationHubClient(notificationHubConnection: NotificationHubConnection, wsClient: WSClient)
    (implicit executionContext: ExecutionContext) {

  import NotificationHubClient.HubResult
  import notificationHubConnection._

  val logger = Logger(classOf[NotificationHubClient])

  private object Endpoints {
    val Registrations = "/registrations/"
    val Messages = "/messages/"
  }

  private def extractContent[T](hubResult: HubResult[AtomEntry[T]]): HubResult[T] = hubResult.map(_.content)

  def create(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationResponse]] = {
    logger.debug(s"Creating new registration: ${rawWindowsRegistration.toXml}")
    request(Endpoints.Registrations)
      .withHeaders("Content-Type" -> "application/atom+xml;type=entry;charset=utf-8")
      .post(rawWindowsRegistration.toXml)
      .map { tryParse[AtomEntry[RegistrationResponse]](Status.OK, Status.CREATED) }
      .map(extractContent)
  }

  def update(registrationId: WNSRegistrationId,
             rawWindowsRegistration: RawWindowsRegistration
              ): Future[HubResult[RegistrationResponse]] = {
    logger.debug(s"Updating registration ($registrationId) with ${rawWindowsRegistration.toXml}")
    request(s"${Endpoints.Registrations}${registrationId.registrationId}")
      .withHeaders("Content-Type" -> "application/atom+xml;type=entry;charset=utf-8")
      .put(rawWindowsRegistration.toXml)
      .map { tryParse[AtomEntry[RegistrationResponse]](Status.OK, Status.CREATED) }
      .map(extractContent)
  }

  def delete(registrationId: WNSRegistrationId): Future[HubResult[Unit]] = {
    logger.debug(s"deleting registration ($registrationId)")
    request(s"${Endpoints.Registrations}${registrationId.registrationId}")
      .withHeaders(
        "If-Match" -> "*",
        "Content-Type" -> "application/atom+xml;type=entry;charset=utf-8"
      )
      .delete()
      .map { response =>
        if (response.status == 200 || response.status == 404) {
          ().right
        } else {
          XmlParser.parseError(response).left
        }
      }
  }

  def sendNotification(azureWindowsPush: AzureRawPush): Future[HubResult[Unit]] = {
    val serviceBusTags = azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList
    logger.debug(s"Sending Azure Raw Notification: $azureWindowsPush")
    request(Endpoints.Messages)
      .withHeaders("X-WNS-Type" -> "wns/raw")
      .withHeaders("ServiceBusNotification-Format" -> "windows")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .withHeaders(serviceBusTags: _*)
      .post(azureWindowsPush.body)
      .map { response =>
        if ((200 until 300).contains(response.status))
          ().right
        else
          XmlParser.parseError(response).left
      }
  }

  private def request(path: String) = {
    val uri = s"$endpoint$path?api-version=2015-01"
    wsClient
      .url(uri)
      .withHeaders("Authorization" -> authorizationHeader(uri))
  }

  def registrationsByTag(tag: String): Future[HubResult[List[RegistrationResponse]]] = {
    request(s"/tags/$tag/registrations")
      .get()
      .map { tryParse[AtomFeedResponse[RegistrationResponse]](Status.OK) }
      .map { hubResult => hubResult.map(_.items) }
  }

  def submitNotificationHubJob(job: NotificationHubJobRequest): Future[HubResult[NotificationHubJob]] = {
    request("/jobs")
      .post(job.toXml)
      .map { tryParse[AtomEntry[NotificationHubJob]](Status.OK, Status.CREATED) }
      .map(extractContent)
  }

  def registrationsByChannelUri(channelUri: String): Future[HubResult[List[RegistrationResponse]]] = {
    request(Endpoints.Registrations)
      .withQueryString("$filter" -> s"ChannelUri eq '$channelUri'")
      .get()
      .map { tryParse[AtomFeedResponse[RegistrationResponse]](Status.OK) }
      .map { hubResult => hubResult.map(_.items) }
  }

  private def tryParse[T](successCode: Int, successCodes: Int*)(response: WSResponse)(implicit reader: XmlReads[T]) = {
    if ((successCode +: successCodes).contains(response.status))
      XmlParser.parse[T](response)
    else {
      logger.error(s"Error returned from Azure endpoint (code: ${response.status} with body: ${response.body}")
      XmlParser.parseError(response).left
    }
  }
}