package notification.authentication

import authentication.AuthAction
import models.Topic
import models.TopicTypes.Newsstand
import notification.services.Configuration
import play.api.Logger
import play.api.mvc.ControllerComponents

class NotificationAuthAction(configuration: Configuration,  controllerComponents: ControllerComponents) extends AuthAction(controllerComponents) {

  override def validApiKey(apiKey: String): Boolean = configuration.apiKeys.contains(apiKey) || configuration.electionRestrictedApiKeys.contains(apiKey)

  override def isPermittedTopic(apiKey: String): Topic => Boolean = {
    if(configuration.newsstandRestrictedApiKeys.contains(apiKey)) {
      topic => {
        topic.`type` match {
          case Newsstand => true
          case other =>
            Logger.warn(s"Received notification of type $other that isn't Newsstand, with the Newsstand api key")
            true
        }
      }
    } else {

      topic => {
        topic.`type` match {
          case Newsstand => {
            Logger.warn(s"Received a Newsstand notification with a non Newsstand api key")
            true
          }
          case _ => true
        }
      }
    }
  }
}
