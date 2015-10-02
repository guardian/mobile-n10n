package services

import javax.inject.Inject

import notifications.providers.{NotificationRegistrar, WindowsNotificationProvider}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

final class NotificationRegistrarSupport @Inject()(wsClient: WSClient, configuration: Configuration)(implicit executionContext: ExecutionContext) {

  val providerConf = configuration.notificationHubConfiguration.fold(error => throw new Exception(error.message), identity)

  val notificationRegistrar: NotificationRegistrar = new WindowsNotificationProvider(
    wsClient = wsClient,
    connectionString = providerConf.connectionString,
    hubName = providerConf.hubName
  )
}