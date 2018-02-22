package notification.authentication

import authentication.AuthAction
import models.Topic
import models.TopicTypes.{ElectionResults, LiveNotification}
import notification.services.Configuration
import play.api.mvc.ControllerComponents

class NotificationAuthAction(configuration: Configuration,  controllerComponents: ControllerComponents) extends AuthAction(controllerComponents) {

  override def validApiKey(apiKey: String): Boolean = configuration.apiKeys.contains(apiKey) || configuration.electionRestrictedApiKeys.contains(apiKey)

  override def isPermittedTopic(apiKey: String): Topic => Boolean = {
    if (configuration.electionRestrictedApiKeys.contains(apiKey)) {
      topic => List(ElectionResults, LiveNotification).contains(topic.`type`)
    } else {
      _ => true
    }
  }
}
