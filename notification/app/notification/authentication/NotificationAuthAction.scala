package notification.authentication

import authentication.AuthAction
import models.Topic
import notification.services.Configuration
import play.api.mvc.ControllerComponents

class NotificationAuthAction(configuration: Configuration,  controllerComponents: ControllerComponents) extends AuthAction(controllerComponents) {

  override def validApiKey(apiKey: String): Boolean = configuration.apiKeys.contains(apiKey)

  override def isPermittedTopic(apiKey: String): Topic => Boolean = _ => true
}
