package notification.services.azure

import azure.NotificationHubClient
import azure.NotificationHubClient.HubResult
import error.NotificationsError
import models.Importance.Major
import models._
import notification.models.Destination.Destination
import notification.models.Push
import notification.services.{NotificationRejected, Senders, _}
import org.joda.time.DateTime
import tracking.Repository._
import tracking.{RepositoryResult, TopicSubscriptionsRepository}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.syntax.either._
import scalaz.syntax.std.option._
import scalaz.{-\/, \/-}


abstract class NotificationsHubSender(
  hubClient: NotificationHubClient,
  configuration: Configuration,
  topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationSender {

  protected def send(push: Push): Future[HubResult[Unit]]

  def sendNotification(push: Push): Future[SenderResult] = {

    def report(recipientsCount: Option[Int]) = SenderReport(
      senderName = Senders.AzureNotificationsHub,
      sentTime = DateTime.now,
      platformStatistics = recipientsCount map { PlatformStatistics(WindowsMobile, _) }
    )

    if (push.notification.importance == Major) {
      for {
        result <- send(push)
        count <- count(push.destination)
      } yield {
        result.fold(
          e => NotificationRejected(NotificationHubSenderError(e.some).some).left,
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

case class NotificationHubSenderError(underlying: Option[NotificationsError]) extends SenderError {
  override def senderName: String = Senders.AzureNotificationsHub
  override def reason: String = s"Sender: $senderName ${ underlying.fold("")(_.reason) }"
}
