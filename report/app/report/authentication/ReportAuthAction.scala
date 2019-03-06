package report.authentication

import authentication.AuthAction
import models.Topic
import play.api.mvc.ControllerComponents
import report.services.Configuration

class ReportAuthAction(configuration: Configuration, controllerComponents: ControllerComponents) extends AuthAction(controllerComponents) {

  val allApiKeys = configuration.apiKeys ++ configuration.reportsOnlyApiKeys

  override def validApiKey(apiKey: String): Boolean = allApiKeys.contains(apiKey)

  override def isPermittedTopic(apiKey: String): Topic => Boolean =
    _ => false
}
