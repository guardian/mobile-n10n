package notification.services.azure

import azure.NotificationHubClient
import error.NotificationsError
import models.Importance.Major
import models._
import notification.models.Destination.Destination
import notification.models.Push
import notification.services.{Senders, _}
import org.joda.time.DateTime
import tracking.Repository._
import tracking.{RepositoryResult, TopicSubscriptionsRepository}

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._


abstract class NotificationsHubSender(
  hubClient: NotificationHubClient,
  configuration: Configuration,
  topicSubscriptionsRepository: TopicSubscriptionsRepository
)
  (implicit ec: ExecutionContext) extends NotificationSender {

  protected def converter: PushConverter

  protected def platform: Platform

  def sendNotification(push: Push): Future[SenderResult] = {

    def report(sendersId: Option[String], recipientsCount: Option[Int]) = SenderReport(
      sendersId = sendersId,
      senderName = Senders.AzureNotificationsHub,
      sentTime = DateTime.now,
      platformStatistics = recipientsCount map { PlatformStatistics(platform , _) }
    )

    converter.toRawPush(push) match {
      case Some(rawPush) if shouldSendToApps(push.notification) =>
        for {
          result <- hubClient.sendNotification(rawPush)
          count <- count(push.destination)
        } yield {
          result.fold(
            e => Left(NotificationHubSenderError(e)),
            id => Right(report(id, count.toOption))
          )
        }

      case _ => Future.successful(Right(report(None, None)))
    }
  }

  protected def shouldSendToApps(notification: Notification) =
    notification.`type` == NotificationType.ElectionsAlert ||
      notification.`type` == NotificationType.LiveEventAlert ||
      notification.`type` == NotificationType.FootballMatchStatus ||
      notification.importance == Major

  private def count(destination: Destination): Future[RepositoryResult[Int]] = sumOf(destination)

  private def sumOf(topics: Set[Topic]): Future[RepositoryResult[Int]] = {
    // Beware: topics must be converted to list so that identical value responses from repository are not treated as the same
    val eventuallySubscriberCounts = topics.toList.map(topicSubscriptionsRepository.count)
    Future.sequence(eventuallySubscriberCounts) map { results =>
      val errors = results.collect { case Left(error) => error }
      val successes = results.collect { case Right(success) => success }
      if (errors.nonEmpty) {
        Left(errors.head)
      } else {
        RepositoryResult(successes.sum)
      }
    }
  }
}

case class NotificationHubSenderError(underlying: NotificationsError) extends SenderError {
  override def senderName: String = Senders.AzureNotificationsHub
  override def reason: String = s"Sender: $senderName ${underlying.reason}"
}
