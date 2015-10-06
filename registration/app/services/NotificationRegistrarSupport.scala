package services

import javax.inject.Inject

import gu.msnotifications.{ConnectionSettings, Endpoint, NotificationHubClient}
import notifications.providers.{NotificationRegistrar, WindowsNotificationProvider}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

final class NotificationRegistrarSupport @Inject()(wsClient: WSClient, configuration: Configuration)(implicit executionContext: ExecutionContext) {
  private def hubConnection = ConnectionSettings(
    endpoint = Endpoint.parse(providerConf.endpointUri),
    keyName =  providerConf.sharedKeyName,
    key = providerConf.sharedKeyValue
  ).buildHubConnection(providerConf.hubName)

  private val providerConf = configuration.notificationHubConfiguration
  private val hubClient = new NotificationHubClient(hubConnection, wsClient)

  val notificationRegistrar: NotificationRegistrar = new WindowsNotificationProvider(wsClient, hubClient)
}