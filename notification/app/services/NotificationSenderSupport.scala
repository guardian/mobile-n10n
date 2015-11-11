package services

import javax.inject.Inject

import gu.msnotifications.{NotificationHubConnection, NotificationHubClient}
import notifications.providers.{NotificationSender, WindowsNotificationProvider}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

final class NotificationSenderSupport @Inject()(wsClient: WSClient, configuration: Configuration)(implicit executionContext: ExecutionContext) {
  private def hubConnection = NotificationHubConnection(
    endpoint = configuration.hubEndpoint,
    sharedAccessKeyName = configuration.hubSecretKeyName,
    sharedAccessKey = configuration.hubSecretKey
  )

  private val hubClient = new NotificationHubClient(hubConnection, wsClient)

  val notificationSender: NotificationSender = new WindowsNotificationProvider(hubClient)
}