package notifications.providers

import gu.msnotifications.{AzureRawPush, NotificationHubClient, RawWindowsRegistration}
import models._
import org.joda.time.DateTime
import play.api.libs.ws.WSClient
import tracking.Repository._
import tracking.{InMemoryTopicSubscriptionsRepository, RepositoryResult}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

class WindowsNotificationProvider(wsClient: WSClient, hubClient: NotificationHubClient)(implicit ec: ExecutionContext)
  extends NotificationRegistrar with NotificationSender {
  override val name = "WNS"

  private val topicSubscriptionsRepository = new InMemoryTopicSubscriptionsRepository

  override def register(registration: Registration): Future[\/[Error, RegistrationResponse]] =
    hubClient.register(RawWindowsRegistration.fromMobileRegistration(registration)).map { hubResult =>
      hubResult.map { _.toRegistrarResponse }
    }

  def sendNotification(push: Push): Future[Error \/ NotificationReport] = for {
    result <- hubClient.sendNotification(AzureRawPush.fromPush(push))
    count <- getCounts(push.destination)
  } yield {
    result map { _ =>
      NotificationReport(
        sentTime = DateTime.now,
        notification = push.notification,
        statistics = NotificationStatistics(Map(WindowsMobile -> count.toOption))
      )
    }
  }

  private def getCounts(destination: Either[Topic, UserId]): Future[RepositoryResult[Int]] = destination match {
    case Left(topic: Topic) => topicSubscriptionsRepository.count(topic)
    case Right(_: UserId) => Future.successful(RepositoryResult(1))
  }

}
