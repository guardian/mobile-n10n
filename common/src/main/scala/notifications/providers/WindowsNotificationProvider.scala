package notifications.providers

import gu.msnotifications
import gu.msnotifications.NotificationHubClient.HubResult
import gu.msnotifications.{WNSRegistrationId, AzureRawPush, NotificationHubClient, ConnectionSettings, RawWindowsRegistration}
import models._
import org.joda.time.DateTime
import play.api.libs.ws.WSClient
import tracking.{RepositoryResult, InMemoryTopicSubscriptionsRepository}
import tracking.Repository._
import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/-, \/}
import scalaz.syntax.either._

class WindowsNotificationProvider(wsClient: WSClient, connectionString: String, hubName: String)(implicit ec: ExecutionContext) extends NotificationRegistrar with NotificationSender {
  override val name = "WNS"

  private def notificationHubOR = for {
    settings <- ConnectionSettings.fromString(connectionString)
  } yield settings.buildNotificationHub(hubName)

  private def notificationHub = notificationHubOR.fold(error => throw new Exception(error.reason), identity)

  private val topicSubscriptionsRepository = new InMemoryTopicSubscriptionsRepository

  private val hubClient = new NotificationHubClient(notificationHub, wsClient)

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

  private def hubResultToRegistrationResponse(hubResult: HubResult[msnotifications.RegistrationResponse]) = hubResult.map { _.toRegistrarResponse }

}

sealed trait WindowsNotificationProviderError extends Error {
  override def providerName: String = "WNS"
}

case class TooManyRegistrationsForChannel(channelUri: String) extends WindowsNotificationProviderError {
  override def reason: String = s"Too many registration for channel $channelUri exist"
}
