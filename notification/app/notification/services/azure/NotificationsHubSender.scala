package notification.services.azure

import azure.NotificationHubClient
import azure.NotificationHubClient.HubResult
import error.NotificationsError
import models.Importance.Major
import models._
import notification.models.Destination.Destination
import notification.models.Push
import notification.services.{NotificationRejected, Senders, _}
import _root_.azure.RawPush
import org.joda.time.DateTime
import tracking.Repository._
import tracking.{RepositoryResult, TopicSubscriptionsRepository}

import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import cats.implicits._


abstract class NotificationsHubSender(
  hubClient: NotificationHubClient,
  configuration: Configuration,
  topicSubscriptionsRepository: TopicSubscriptionsRepository
)
  (implicit ec: ExecutionContext) extends NotificationSender {

  protected def converter: PushConverter

  def sendNotification(push: Push): Future[SenderResult] = {

    def report(sendersId: Option[String], recipientsCount: Option[Int]) = SenderReport(
      sendersId = sendersId,
      senderName = Senders.AzureNotificationsHub,
      sentTime = DateTime.now,
      platformStatistics = recipientsCount map { PlatformStatistics(WindowsMobile, _) }
    )

    converter.toRawPush(push) match {
      case Some(rawPush) if shouldSendToApps(push.notification) =>
        for {
          result <- hubClient.sendNotification(rawPush)
          count <- count(push.destination)
        } yield {
          result.fold(
            e => NotificationRejected(NotificationHubSenderError(e.some).some).left,
            id => report(id, count.toOption).right
          )
        }

      case _ => Future.successful(report(None, None).right)
    }
  }

  protected def shouldSendToApps(notification: Notification) =
    notification.`type` == NotificationType.ElectionsAlert ||
      notification.`type` == NotificationType.LiveEventAlert ||
      notification.`type` == NotificationType.FootballMatchStatus ||
      notification.importance == Major

  private def count(destination: Destination): Future[RepositoryResult[Int]] = destination match {
    case Left(topics: Set[Topic]) => sumOf(topics)
    case Right(_: UniqueDeviceIdentifier) => Future.successful(RepositoryResult(1))
  }

  private def sumOf(topics: Set[Topic]): Future[RepositoryResult[Int]] = {
    // Beware: topics must be converted to list so that identical value responses from repository are not treated as the same
    val eventuallySubscriberCounts = topics.toList.map(topicSubscriptionsRepository.count)
    Future.sequence(eventuallySubscriberCounts) map { results =>
      val errors = results.collect { case Xor.Left(error) => error }
      val successes = results.collect { case Xor.Right(success) => success }
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
