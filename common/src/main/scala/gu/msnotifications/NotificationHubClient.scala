package gu.msnotifications

import gu.msnotifications.HubFailure.{HubServiceError, HubParseFailed}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.async.Async
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * https://msdn.microsoft.com/en-us/library/azure/dn223264.aspx
 */
final class NotificationHubClient(notificationHubConnection: NotificationHubConnection, wsClient: WSClient)
                                 (implicit executionContext: ExecutionContext) {

  import notificationHubConnection._

  type HubResult[T] = Either[HubFailure, T]

  def register(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationId]] = {
    val uri = s"""$notificationsHubUrl/registrations/?api-version=2015-01"""
    Async.async {
      processRegistrationResponse {
        Async.await {
          wsClient
            .url(uri)
            .withHeaders("Authorization" -> authorizationHeader(uri))
            .post(rawWindowsRegistration.toXml)
        }
      }
    }
  }

  def update(registrationId: RegistrationId,
             rawWindowsRegistration: RawWindowsRegistration
              ): Future[HubResult[RegistrationId]] = {
    val uri = s"""$notificationsHubUrl/registration/${registrationId.registrationId}?api-version=2015-01"""
    Async.async {
      processRegistrationResponse {
        Async.await {
          wsClient
            .url(uri)
            .withHeaders("Authorization" -> authorizationHeader(uri))
            .post(rawWindowsRegistration.toXml)
        }
      }
    }
  }

  def sendPush(azureWindowsPush: AzureXmlPush): Future[HubResult[Unit]] = {
    val uri = s"""$notificationsHubUrl/messages/?api-version=2015-01"""
    Async.async {
      processResponse {
        Async.await {
          wsClient
            .url(uri)
            .withHeaders("X-WNS-Type" -> azureWindowsPush.wnsType)
            .withHeaders("ServiceBusNotification-Format" -> "windows")
            .withHeaders("Authorization" -> authorizationHeader(uri))
            .withHeaders(azureWindowsPush.tagQuery.map(tagQuery => "ServiceBusNotification-Tags" -> tagQuery).toSeq: _*)
            .post(azureWindowsPush.xml)
        }
      }.right.map(_ => ())
    }
  }

  /**
   * This is used for health-checking only: return the title of the feed,
   * which is expected to be 'Registrations'.
   * @return the title of the feed if it succeeds getting one
   */
  def fetchRegistrationsListEndpoint: Future[HubResult[String]] = {
    val url = s"""$notificationsHubUrl/registrations/?api-version=2015-01"""
    Async.async {
      processResponse {
        Async.await {
          wsClient.url(url)
            .withHeaders("Authorization" -> authorizationHeader(url))
            .get()
        }
      }.right.map(xml => (xml \ "title").text)
    }
  }

  private def processResponse(response: WSResponse): HubResult[scala.xml.Elem] = {
    Try(response.xml).toOption match {
      case None =>
        Left {
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
      case Some(xml) =>
        Right(response.xml)
    }
  }

  private def processRegistrationResponse(response: WSResponse): HubResult[RegistrationId] = {
    Try(response.xml).toOption match {
      case None =>
        Left {
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
      case Some(xml) =>
        val parser = RegistrationResponseParser(xml)
        parser.registrationId match {
          case Some(updatedRegistrationId) =>
            Right(RegistrationId(updatedRegistrationId))
          case None =>
            Left {
              parser.error match {
                case Some((code, description)) =>
                  HubServiceError(reason = description, code = code)
                case None =>
                  HubParseFailed(body = response.body, reason = "Received in valid XML")
              }
            }
        }
    }
  }

  private case class ResponseParser(xml: scala.xml.Elem) {

    def error: Option[(Int, String)] = {
      for {
        code <- xml \ "Code"
        detail <- xml \ "Detail"
      } yield code.text.toInt -> detail.text
    }.headOption

  }

  private case class RegistrationResponseParser(xml: scala.xml.Elem) {

    def error = ResponseParser(xml).error

    def registrationId: Option[String] = {
      (xml \\ "RegistrationId").map(_.text).headOption
    }

  }

}