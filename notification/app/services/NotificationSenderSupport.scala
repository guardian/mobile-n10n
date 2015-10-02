package services

import javax.inject.Inject

import notifications.providers.{NotificationSender, WindowsNotificationProvider}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

final class NotificationSenderSupport @Inject()(wsClient: WSClient, configuration: Configuration)(implicit executionContext: ExecutionContext) {

  val providerConf = configuration.notificationHubConfiguration.fold(error => throw new Exception(error.message), identity)

  val notificationSender: NotificationSender = new WindowsNotificationProvider(
    wsClient = wsClient,
    connectionString = providerConf.connectionString,
    hubName = providerConf.hubName
  )
}