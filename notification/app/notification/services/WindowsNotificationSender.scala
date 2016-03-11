package notification.services

import azure.NotificationHubClient
import error.NotificationsError
import models.Importance.Major
import models._
import notification.models.Destination.Destination
import notification.models.Push
import org.joda.time.DateTime
import tracking.Repository._
import tracking.{InMemoryTopicSubscriptionsRepository, RepositoryResult}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{\/-, -\/}
import scalaz.syntax.either._
import scalaz.syntax.std.option._

class WindowsNotificationSender(hubClient: NotificationHubClient, configuration: Configuration)(implicit ec: ExecutionContext) extends NotificationSender {
  private val topicSubscriptionsRepository = new InMemoryTopicSubscriptionsRepository

  private val azureRawPushConverter = new AzureRawPushConverter(configuration)

  def sendNotification(push: Push): Future[SenderResult] = {

    def report(recipentsCount: Option[Int]) = SenderReport(
      senderName = Senders.Windows,
      sentTime = DateTime.now,
      platformStatistics = recipentsCount map { PlatformStatistics(WindowsMobile, _) }
    )

    if (push.notification.importance == Major) {
      for {
        result <- hubClient.sendNotification(azureRawPushConverter.toAzureRawPush(push))
        count <- count(push.destination)
      } yield {
        result.fold(
          e => NotificationRejected(WindowsNotificationSenderError(e.some).some).left,
          _ => report(count.toOption).right
        )
      }
    } else {
      Future.successful(report(None).right)
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

case class WindowsNotificationSenderError(underlying: Option[NotificationsError]) extends SenderError {
  override def senderName: String = Senders.Windows
  override def reason: String = s"Sender: $senderName ${ underlying.fold("")(_.reason) }"
}
