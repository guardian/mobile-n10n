package registration.services

import javax.inject.Inject

import aws.AsyncDynamo
import azure.{NotificationHubClient, NotificationHubConnection}
import com.amazonaws.regions.Regions.EU_WEST_1
import com.google.inject.ImplementedBy
import models.{Registration, WindowsMobile}
import play.api.libs.ws.WSClient
import registration.services.windows.WindowsNotificationRegistrar
import tracking.DynamoTopicSubscriptionsRepository

import scala.concurrent.ExecutionContext
import scalaz.\/
import scalaz.syntax.either._

@ImplementedBy(classOf[NotificationRegistrarSupport])
trait RegistrarSupport {
  def registrarFor(registration: Registration): \/[String, NotificationRegistrar]
}

final class NotificationRegistrarSupport @Inject()(wsClient: WSClient, configuration: Configuration)
  (implicit executionContext: ExecutionContext) extends RegistrarSupport {

  private def hubConnection = NotificationHubConnection(
    endpoint = configuration.hubEndpoint,
    sharedAccessKeyName =  configuration.hubSharedAccessKeyName,
    sharedAccessKey = configuration.hubSharedAccessKey
  )

  private val hubClient = new NotificationHubClient(hubConnection, wsClient)


  private lazy val notificationRegistrar: NotificationRegistrar = {
    val topicSubscriptionRepository = new DynamoTopicSubscriptionsRepository(
      AsyncDynamo(region = EU_WEST_1),
      configuration.dynamoTopicsTableName
    )
    new WindowsNotificationRegistrar(hubClient, topicSubscriptionRepository)
  }

  override def registrarFor(registration: Registration): String \/ NotificationRegistrar = registration match {
    case Registration(_, WindowsMobile, _, _) => notificationRegistrar.right
    case _ => "Unsupported platform".left
  }
}
