package azure

import play.api.Logger
import play.api.http.{Status, Writeable}
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import utils.WSImplicits._
import cats.syntax.either._

case class NotificationHubError(message: String)

object NotificationHubClient {
  type HubResult[T] = Either[HubFailure, T]
}
/**
 * https://msdn.microsoft.com/en-us/library/azure/dn223264.aspx
 */
class NotificationHubClient(val notificationHubConnection: NotificationHubConnection, wsClient: WSClient)
    (implicit executionContext: ExecutionContext) {

  import NotificationHubClient.HubResult
  import notificationHubConnection._

  val logger = Logger(classOf[NotificationHubClient])

  private object Endpoints {
    val Registrations = "/registrations/"
    val Messages = "/messages/"
  }

  private def extractContent[T](hubResult: HubResult[AtomEntry[T]]): HubResult[T] = hubResult.map(_.content)

  def notificationDetails(id: String): Future[HubResult[NotificationDetails]] = {
    logger.debug(s"Requesting details for notification $id")
    request(s"${Endpoints.Messages}$id")
      .get()
      .map { tryParse[NotificationDetails](Status.OK) }
  }

  def create(rawRegistration: NotificationsHubRegistration): Future[HubResult[RegistrationResponse]] = {
    logger.debug(s"Creating new registration: ${rawRegistration.toXml}")
    request(Endpoints.Registrations)
      .addHttpHeaders("Content-Type" -> "application/atom+xml;type=entry;charset=utf-8")
      .post(rawRegistration.toXml)
      .map { tryParse[AtomEntry[RegistrationResponse]](Status.OK, Status.CREATED) }
      .map(extractContent)
  }

  def update(registrationId: NotificationHubRegistrationId,
             rawRegistration: NotificationsHubRegistration
              ): Future[HubResult[RegistrationResponse]] = {
    logger.debug(s"Updating registration ($registrationId) with ${rawRegistration.toXml}")
    request(s"${Endpoints.Registrations}${registrationId.registrationId}")
      .addHttpHeaders("Content-Type" -> "application/atom+xml;type=entry;charset=utf-8")
      .put(rawRegistration.toXml)
      .map { tryParse[AtomEntry[RegistrationResponse]](Status.OK, Status.CREATED) }
      .map(extractContent)
  }

  def delete(registrationId: NotificationHubRegistrationId): Future[HubResult[Unit]] = {
    logger.debug(s"deleting registration ($registrationId)")
    request(s"${Endpoints.Registrations}${registrationId.registrationId}")
      .addHttpHeaders(
        "If-Match" -> "*",
        "Content-Type" -> "application/atom+xml;type=entry;charset=utf-8"
      )
      .delete()
      .map { response =>
        if (response.status == 200 || response.status == 404) {
          Right(())
        } else {
          Left(XmlParser.parseError(response))
        }
      }
  }

  def sendNotification(rawPush: RawPush): Future[HubResult[Option[String]]] = {
    def retry(count: Int): Future[HubResult[Option[String]]] = {
      val serviceBusTags = rawPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toList
      logger.debug(s"Sending Raw Notification: $rawPush")
      rawPush.post(
        request(Endpoints.Messages)
          .addHttpHeaders(rawPush.extraHeaders: _*)
          .addHttpHeaders("ServiceBusNotification-Format" -> rawPush.format)
          .addHttpHeaders(serviceBusTags: _*)
      ).flatMap {
        case response if response.isSuccess =>
          Future.successful(Right(response.header("Location")))
        case response =>
          val error = XmlParser.parseError(response)
          // retry three times, fail on the third
          if (count < 3 && error.reason.contains("Azure Notifications Hub Server Busy")) {
            logger.error(
              s"Unable to push notification, try $count/3. " +
                s"Got response code: ${response.status} " +
                s"got the following error: $error for the notification $rawPush"
            )
            retry(count + 1)
          } else {
            logger.error(
              s"Unable to push notification, " +
                s"response code: ${response.status} " +
                s"got the following error: $error for the notification $rawPush"
            )
            Future.successful(Left(error))
          }
      }
    }
    retry(1)
  }

  private def request(path: String, queryParams: Map[String, String] = Map.empty) = {
    val queryString = queryParams.updated("api-version", "2015-01").map({ case (k, v) => s"$k=$v"}).mkString("?", "&", "")
    val uri = s"$endpoint$path$queryString"
    wsClient
      .url(uri)
      .addHttpHeaders("Authorization" -> authorizationHeader(uri))
  }

  def registrationsByTag(tag: String, cursor: Option[String] = None): Future[HubResult[Registrations]] = {
    val params = cursor.map(c => Map("ContinuationToken" -> c)).getOrElse(Map.empty)
    request(s"/tags/$tag/registrations", params)
      .get()
      .map { response =>
        tryParse[AtomFeedResponse[RegistrationResponse]](Status.OK)(response).map { parsed =>
          Registrations(
            registrations = parsed.items,
            cursor = response.header("X-MS-ContinuationToken")
          )
        }
      }
  }

  def submitNotificationHubJob(job: NotificationHubJobRequest): Future[HubResult[NotificationHubJob]] = {
    request("/jobs")
      .post(job.toXml)
      .map { tryParse[AtomEntry[NotificationHubJob]](Status.OK, Status.CREATED) }
      .map(extractContent)
  }

  def registrationsByChannelUri(channelUri: String): Future[HubResult[List[RegistrationResponse]]] = {
    request(Endpoints.Registrations)
      .withQueryStringParameters("$filter" -> s"ChannelUri eq '$channelUri'")
      .get()
      .map { tryParse[AtomFeedResponse[RegistrationResponse]](Status.OK) }
      .map { hubResult => hubResult.map(_.items) }
  }

  private def tryParse[T](successCode: Int, successCodes: Int*)(response: WSResponse)(implicit reader: XmlReads[T]) = {
    if ((successCode +: successCodes).contains(response.status))
      XmlParser.parse[T](response)
    else {
      logger.error(s"Error returned from Azure endpoint (code: ${response.status} with body: ${response.body}")
      Left(XmlParser.parseError(response))
    }
  }
}