package notification.services

import azure.NotificationHubClient
import models.Importance.Major
import models._
import notification.models.Destination.Destination
import notification.models.Push
import org.joda.time.DateTime
import providers.Error
import tracking.Repository._
import tracking.{InMemoryTopicSubscriptionsRepository, RepositoryResult}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{\/-, -\/, \/}
import scalaz.syntax.either._

class WindowsNotificationSender(hubClient: NotificationHubClient, configuration: Configuration)(implicit ec: ExecutionContext) extends NotificationSender {
  override val name = "WNS"

  private val topicSubscriptionsRepository = new InMemoryTopicSubscriptionsRepository

  private val azureRawPushConverter = new AzureRawPushConverter(configuration)

  def sendNotification(push: Push): Future[Error \/ NotificationReport] = {

    def report(stats: Map[Platform, Option[Int]]) = NotificationReport.create(
      sentTime = DateTime.now,
      notification = push.notification,
      statistics = NotificationStatistics(stats)
    )

    if (push.notification.importance == Major) {
      for {
        result <- hubClient.sendNotification(azureRawPushConverter.toAzureRawPush(push))
        count <- count(push.destination)
      } yield {
        result.map(_ => report(Map(WindowsMobile -> count.toOption)))
      }
    } else {
      Future.successful(report(Map.empty).right)
    }
  }

  private def count(destination: Destination): Future[RepositoryResult[Int]] = destination match {
    case Left(topics: Set[Topic]) => sumOf(topics)
    case Right(_: UserId) => Future.successful(RepositoryResult(1))
  }

  private def sumOf(topics: Set[Topic]): Future[RepositoryResult[Int]] = {
    Future.sequence(topics.map(topicSubscriptionsRepository.count)) map { results =>
      val errors = results.collect { case -\/(error) => error }
      val successes = results.collect { case \/-(success) => success }

      if (errors.nonEmpty) {
        errors.head.left
      } else {
        RepositoryResult(successes.sum)
      }
    }
  }
}
