package notification.services

import javax.inject.Inject

import azure.{NotificationHubClient, NotificationHubConnection}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

final class NotificationSenderSupport @Inject()(wsClient: WSClient, configuration: Configuration)(implicit executionContext: ExecutionContext) {
  private def hubConnection = NotificationHubConnection(
    endpoint = configuration.hubEndpoint,
    sharedAccessKeyName = configuration.hubSharedAccessKeyName,
    sharedAccessKey = configuration.hubSharedAccessKey
  )

  private val hubClient = new NotificationHubClient(hubConnection, wsClient)

  val notificationSender: NotificationSender = new WindowsNotificationSender(hubClient)
}