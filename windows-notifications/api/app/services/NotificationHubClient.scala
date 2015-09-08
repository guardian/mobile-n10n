package services

import javax.inject.Inject

import gu.msnotifications.{RegistrationId, RawWindowsRegistration}
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
            .url(notificationHub.PostRegistrations.url)
            .withHeaders("Authorization" -> notificationHub.PostRegistrations.authHeader)
            .post(rawWindowsRegistration.toXml)
        }
      }
    }
  }

  def update(registrationId: RegistrationId, rawWindowsRegistration: RawWindowsRegistration): Future[HubResult[RegistrationId]] = {
    val updateRegistration = notificationHub.UpdateRegistration(registrationId)
    Async.async {
      processRegistrationResponse {
        Async.await {
          wsClient
            .url(updateRegistration.url)
            .withHeaders("Authorization" -> updateRegistration.authHeader)
            .post(rawWindowsRegistration.toXml)
        }
      }
    }
  }

  def fetchSomeRegistrations: Future[String] = {
    Async.async {
      processResponse {
        Async.await {
          wsClient.url(notificationHub.ListRegistrations.url)
            .withHeaders("Authorization" -> notificationHub.ListRegistrations.authHeader)
            .get()
        }
      }.toString
    }
  }

  private def processResponse(response: WSResponse): Either[HubResult[Nothing], scala.xml.Elem] = {
    Try(response.xml).toOption match {
      case None =>
        Left {
          if (response.status != 200)
            ServiceError(
              reason = response.statusText,
              code = response.status
            )
          else ServiceParseFailed(
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