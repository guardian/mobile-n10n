package services

import javax.inject.Inject

import gu.msnotifications.{NotificationHubClient, ConnectionSettings}
import play.api.libs.ws.WSClient
import play.api.mvc.{Result, Results, Request, ActionBuilder}
import tracking.{InMemoryTopicSubscriptionsRepository, InMemoryNotificationReportRepository}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext
import com.gu.conf.ConfigurationFactory
import scalaz.\/
import scalaz.std.option.optionSyntax._

case class ErrorMessage(message: String)

final class ReportConfiguration @Inject()(wsClient: WSClient)(implicit executionContext: ExecutionContext) {

  private lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "notification",
    webappConfDirectory = "gu-conf"
  )

  private def getConfigurationProperty(name: String): ErrorMessage \/ String =
    conf.getStringProperty(name) \/> ErrorMessage(s"Could not find property $name")

  val notificationReportRepository = new InMemoryNotificationReportRepository
  val topicSubscriptionRepository = new InMemoryTopicSubscriptionsRepository

  def AuthenticatedAction: ActionBuilder[Request] = new ActionBuilder[Request] with Results {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      request.getQueryString("api-key") match {
        case Some(key) if validApiKey(key) => block(request)
        case _ => Future.successful(Unauthorized("A valid API key is required."))
      }
    }
  }

  def apiKey = getConfigurationProperty("gu.msnotifications.admin-api-key")

  private def validApiKey(candidate: String): Boolean = apiKey.toOption.contains(candidate)
}
