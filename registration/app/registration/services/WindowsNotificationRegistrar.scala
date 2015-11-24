package registration.services

import azure.NotificationHubClient.HubResult
import azure.{Tags, NotificationHubClient, RawWindowsRegistration, WNSRegistrationId}
import models._
import providers.Error

import scala.concurrent.{ExecutionContext, Future}
import scalaz.syntax.either._
import scalaz.{-\/, \/, \/-}
import scalaz.std.option.optionSyntax._

class WindowsNotificationRegistrar(hubClient: NotificationHubClient)(implicit ec: ExecutionContext)
  extends NotificationRegistrar {

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

  private def hubResultToRegistrationResponse(hubResult: HubResult[azure.RegistrationResponse]) =
    hubResult.flatMap(toRegistrarResponse)

  def toRegistrarResponse(resp: azure.RegistrationResponse): UserIdNotInTags \/ RegistrationResponse = {
    val tagsFromUris = Tags.fromStrings(resp.tags.toSet)
    for {
      userId <- tagsFromUris.findUserId \/> UserIdNotInTags()
    } yield RegistrationResponse(
      deviceId = resp.channelUri,
      WindowsMobile,
      userId = userId,
      topics = tagsFromUris.decodedTopics
    )
  }

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
