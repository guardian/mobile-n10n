package services

import javax.inject.Inject

import gu.msnotifications.RawWindowsRegistration
import models.RegistrationId
import play.api.libs.ws.{WSClient, WSResponse}
import services.HubResult.{Successful, ServiceParseFailed, ServiceError}

import scala.async.Async
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

final class NotificationHubClient @Inject()(apiConfiguration: ApiConfiguration, wsClient: WSClient)(implicit executionContext: ExecutionContext) {

  import apiConfiguration.notificationHub

  def register(rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationId]] = {
    Async.async {
      processRegistrationResponse {
        Async.await {
          wsClient
            .url(notificationHub.registrationsPostUrl)
            .withHeaders("Authorization" -> notificationHub.registrationsAuthorizationHeader)
            .post(rawWindowsRegistration.toXml)
        }
      }
    }
  }

  def update(registrationId: RegistrationId, rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationId]] = {
    Async.async {
      processRegistrationResponse {
        Async.await {
          wsClient
            .url(notificationHub.registrationUrl(registrationId.registrationId))
            .withHeaders("Authorization" -> notificationHub.registrationAuthorizationHeader(registrationId.registrationId))
            .post(rawWindowsRegistration.toXml)
        }
      }
    }
  }

  private def processRegistrationResponse(response: WSResponse): HubResult[RegistrationId] = {
    Try(response.xml).toOption match {
      case None =>
        if (response.status != 200)
          ServiceError(
            reason = response.statusText,
            code = response.status
          )
        else ServiceParseFailed(
          body = response.body,
          reason = "Failed to find any XML"
        )
      case Some(xml) =>
        val parser = notificationHub.RegistrationResponseParser(xml)
        parser.registrationId match {
          case Some(updatedRegistrationId) =>
            Successful(RegistrationId(updatedRegistrationId))
          case None =>
            parser.error match {
              case Some((code, description)) =>
                ServiceError(reason = description, code = code)
              case None =>
                ServiceParseFailed(body = response.body, reason = "Received in valid XML")
            }
        }
    }
  }

}