package notification.services

import azure.NotificationHubClient
import azure.NotificationHubClient.HubResult
import error.NotificationsError
import models.Importance.Major
import models._
import notification.models.Destination.Destination
import notification.models.Push
import org.joda.time.DateTime
import tracking.Repository._
import tracking.{TopicSubscriptionsRepository, RepositoryResult}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{\/-, -\/}
import scalaz.syntax.either._
import scalaz.syntax.std.option._

class WNSNotificationSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends AzureNotificationSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  protected val azureRawPushConverter = new AzureWNSPushConverter(configuration)

  override protected def send(push: Push): Future[HubResult[Unit]] =
    hubClient.sendWNSNotification(azureRawPushConverter.toRawPush(push))
}

class GCMNotificationSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends AzureNotificationSender(hubClient, configuration, topicSubscriptionsRepository)(ec) {

  protected val azureRawPushConverter = new AzureGCMPushConverter(configuration)

  override protected def send(push: Push): Future[HubResult[Unit]] =
    hubClient.sendGCMNotification(azureRawPushConverter.toRawPush(push))
}

abstract class AzureNotificationSender(
  hubClient: NotificationHubClient,
  configuration: Configuration,
  topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationSender {

  protected def send(push: Push): Future[HubResult[Unit]]

  def sendNotification(push: Push): Future[SenderResult] = {

    def report(recipientsCount: Option[Int]) = SenderReport(
      senderName = Senders.Windows,
      sentTime = DateTime.now,
      platformStatistics = recipientsCount map { PlatformStatistics(WindowsMobile, _) }
    )

    if (push.notification.importance == Major) {
      for {
        result <- send(push)
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
    // Beware: topics must be converted to list so that identical value responses from repository are not treated as the same
    val eventuallySubscriberCounts = topics.toList.map(topicSubscriptionsRepository.count)
    Future.sequence(eventuallySubscriberCounts) map { results =>
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
