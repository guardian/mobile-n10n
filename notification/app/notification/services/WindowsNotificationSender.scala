package notification.services

import azure.NotificationHubClient
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

class WindowsNotificationSender(hubClient: NotificationHubClient, configuration: Configuration, topicSubscriptionsRepository: TopicSubscriptionsRepository)
  (implicit ec: ExecutionContext) extends NotificationSender {

  private val azurePushConverter = new AzureRawPushConverter(configuration, AzureRawPushConverter.topicEncoder)
  private val backwardCompatiblePushConverter = new AzureRawPushConverter(configuration, AzureRawPushConverter.backwardCompatibleTopicEncoder)

  def sendNotification(push: Push): Future[SenderResult] =
    if (push.notification.importance == Major) {
      for {
        results <- Future.sequence(hubRequests(push))
        count <- count(push.destination)
      } yield {
        val errors = results.collect { case -\/(error) => error }
        if (errors.isEmpty)
          report(count.toOption).right
        else
          NotificationRejected(WindowsNotificationSenderError(errors).some).left
      }
    } else {
      Future.successful(report(None).right)
    }

  private def hubRequests(push: Push) = push.destination match {
    case Left(_: Set[Topic]) => Seq(
      hubClient.sendNotification(azurePushConverter.toAzureRawPush(push)),
      hubClient.sendNotification(backwardCompatiblePushConverter.toAzureRawPush(push))
    )
    case _ => Seq(hubClient.sendNotification(azurePushConverter.toAzureRawPush(push)))
  }

  private def report(recipientsCount: Option[Int]) = SenderReport(
    senderName = Senders.Windows,
    sentTime = DateTime.now,
    platformStatistics = recipientsCount map { PlatformStatistics(WindowsMobile, _) }
  )

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

case class WindowsNotificationSenderError(underlying: Seq[NotificationsError]) extends SenderError {
  override def senderName: String = Senders.Windows

  override def reason: String = s"Sender: $senderName ${ underlying.map(_.reason).mkString }"
}
