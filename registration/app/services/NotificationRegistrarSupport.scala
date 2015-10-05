package services

import javax.inject.Inject

import com.google.inject.ImplementedBy
import models.{Registration, WindowsMobile}
import notifications.providers.{NotificationRegistrar, WindowsNotificationProvider}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scalaz.\/
import scalaz.syntax.either._

@ImplementedBy(classOf[NotificationRegistrarSupport])
trait RegistrarSupport {
  def registrarFor(registration: Registration): \/[String, NotificationRegistrar]
}

final class NotificationRegistrarSupport @Inject()(wsClient: WSClient, configuration: Configuration)(implicit executionContext: ExecutionContext) extends RegistrarSupport {

  private val providerConf = configuration.notificationHubConfiguration

  private lazy val notificationRegistrar: NotificationRegistrar = new WindowsNotificationProvider(
    wsClient = wsClient,
    connectionString = providerConf.connectionString,
    hubName = providerConf.hubName
  )

  override def registrarFor(registration: Registration): \/[String, NotificationRegistrar] = registration match {
    case Registration(_, WindowsMobile, _, _) => notificationRegistrar.right
    case _ => "Unsupported platform".left
  }
}