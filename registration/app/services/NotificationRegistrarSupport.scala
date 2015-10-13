package services

import javax.inject.Inject

import com.google.inject.ImplementedBy
import models.{Registration, WindowsMobile}
import gu.msnotifications.{ConnectionSettings, Endpoint, NotificationHubClient}
import notifications.providers.{NotificationRegistrar, WindowsNotificationProvider}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scalaz.\/
import scalaz.syntax.either._

@ImplementedBy(classOf[NotificationRegistrarSupport])
trait RegistrarSupport {
  def registrarFor(registration: Registration): \/[String, NotificationRegistrar]
}

final class NotificationRegistrarSupport @Inject()(wsClient: WSClient, configuration: Configuration)
  (implicit executionContext: ExecutionContext) extends RegistrarSupport {

  private def hubConnection = ConnectionSettings(
    endpoint = Endpoint.parse(providerConf.endpointUri),
    keyName =  providerConf.sharedKeyName,
    key = providerConf.sharedKeyValue
  ).buildHubConnection(providerConf.hubName)

  private val providerConf = configuration.notificationHubConfiguration
  private val hubClient = new NotificationHubClient(hubConnection, wsClient)

  private lazy val notificationRegistrar: NotificationRegistrar = new WindowsNotificationProvider(hubClient)

  override def registrarFor(registration: Registration): \/[String, NotificationRegistrar] = registration match {
    case Registration(_, WindowsMobile, _, _) => notificationRegistrar.right
    case _ => "Unsupported platform".left
  }
}
