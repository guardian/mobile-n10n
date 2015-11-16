package notifications.providers

import azure.{RawWindowsRegistration, WNSRegistrationId, AzureRawPush, NotificationHubClient}
import NotificationHubClient.HubResult
import models._
import org.joda.time.DateTime
import tracking.Repository._
import tracking.{InMemoryTopicSubscriptionsRepository, RepositoryResult}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.syntax.either._
import scalaz.{-\/, \/, \/-}

class WindowsNotificationProvider(hubClient: NotificationHubClient)(implicit ec: ExecutionContext)
  extends NotificationRegistrar with NotificationSender {
  override val name = "WNS"

  private val topicSubscriptionsRepository = new InMemoryTopicSubscriptionsRepository

  override def register(registration: Registration): Future[\/[Error, RegistrationResponse]] = {
    def createNewRegistration = hubClient
      .create(RawWindowsRegistration.fromMobileRegistration(registration))
      .map(hubResultToRegistrationResponse)

    def updateRegistration(regId: WNSRegistrationId) = hubClient
      .update(regId, RawWindowsRegistration.fromMobileRegistration(registration))
      .map(hubResultToRegistrationResponse)

    val channelUri = registration.deviceId
    hubClient.registrationsByChannelUri(channelUri).flatMap {
      case \/-(Nil) => createNewRegistration
      case \/-(existing :: Nil) => updateRegistration(existing.registration)
      case \/-(_ :: _ :: _) => Future.successful(TooManyRegistrationsForChannel(channelUri).left)
      case -\/(e: Error) => Future.successful(e.left)
    }
  }

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

  private def hubResultToRegistrationResponse(hubResult: HubResult[azure.RegistrationResponse]) =
    hubResult.flatMap(_.toRegistrarResponse)

}

sealed trait WindowsNotificationProviderError extends Error {
  override def providerName: String = "WNS"
}

case class TooManyRegistrationsForChannel(channelUri: String) extends WindowsNotificationProviderError {
  override def reason: String = s"Too many registration for channel $channelUri exist"
}

case class UserIdNotInTags() extends WindowsNotificationProviderError {
  override def reason: String = "Could not find userId in response from Hub"
}
