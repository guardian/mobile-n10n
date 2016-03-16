package notification.services

import javax.inject.Inject

import aws.AsyncDynamo
import azure.{NotificationHubClient, NotificationHubConnection}
import com.amazonaws.regions.Regions.EU_WEST_1
import play.api.libs.ws.WSClient
import tracking.{DynamoTopicSubscriptionsRepository, TopicSubscriptionsRepository}

import scala.concurrent.ExecutionContext

class NotificationSenderSupport @Inject()(wsClient: WSClient, configuration: Configuration)(implicit executionContext: ExecutionContext) {
  private def hubConnection = NotificationHubConnection(
    endpoint = configuration.hubEndpoint,
    sharedAccessKeyName = configuration.hubSharedAccessKeyName,
    sharedAccessKey = configuration.hubSharedAccessKey
  )

  private val hubClient = new NotificationHubClient(hubConnection, wsClient)


  val notificationSender: NotificationSender = {
    val topicSubscriptionsRepository: TopicSubscriptionsRepository = new DynamoTopicSubscriptionsRepository(
      AsyncDynamo(EU_WEST_1),
      configuration.dynamoTopicsTableName
    )
    new WindowsNotificationSender(hubClient, configuration, topicSubscriptionsRepository)
  }
}