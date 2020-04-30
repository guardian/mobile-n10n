package notification.authentication

import authentication.AuthAction
import models.TopicType
import models.TopicTypes.Newsstand
import notification.services.Configuration
import org.slf4j.{Logger, LoggerFactory}
import play.api.mvc.ControllerComponents

class NotificationAuthAction(configuration: Configuration, controllerComponents: ControllerComponents) extends AuthAction(controllerComponents) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def validApiKey(apiKey: String): Boolean = configuration.apiKeys.contains(apiKey) || configuration.newsstandRestrictedApiKeys.contains(apiKey)

  override def isPermittedTopicType(apiKey: String): TopicType => Boolean = { topicType => {
    def checkApiKeyForTopic(apiKeysForTopic: Set[String], configName: String): Boolean = {
      val matched = apiKeysForTopic.contains(apiKey)
      if (!matched) logger.warn(s"Api key cannot be used for $topicType. Expected in $configName keys")
      matched
    }

    topicType match {
      case Newsstand => checkApiKeyForTopic(configuration.newsstandRestrictedApiKeys, "newsstand restricted")
      case _ => checkApiKeyForTopic(configuration.apiKeys, "api")
    }
  }
  }
}
