package notification.services

import azure.{NotificationHubClient, AzureRawPush}
import models._
import org.joda.time.DateTime
import providers.Error
import tracking.Repository._
import tracking.{InMemoryTopicSubscriptionsRepository, RepositoryResult}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

class WindowsNotificationSender(hubClient: NotificationHubClient)(implicit ec: ExecutionContext) extends NotificationSender {
  override val name = "WNS"

  private val topicSubscriptionsRepository = new InMemoryTopicSubscriptionsRepository

  def sendNotification(push: Push): Future[Error \/ NotificationReport] = for {
    result <- hubClient.sendNotification(AzureRawPush.fromPush(push))
    count <- getCounts(push.destination)
  } yield {
      result map { _ =>
        NotificationReport.create(
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
