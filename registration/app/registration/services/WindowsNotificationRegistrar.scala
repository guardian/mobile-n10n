package registration.services

import azure.NotificationHubClient.HubResult
import azure.{Tag, Tags, NotificationHubClient, RawWindowsRegistration}
import models._
import play.api.Logger
import providers.Error

import scala.concurrent.{ExecutionContext, Future}
import scalaz.syntax.either._
import scalaz.{-\/, \/, \/-}
import scalaz.std.option.optionSyntax._

class WindowsNotificationRegistrar(hubClient: NotificationHubClient)(implicit ec: ExecutionContext)
  extends NotificationRegistrar {

  val logger = Logger(classOf[WindowsNotificationRegistrar])

  override def register(lastKnownChannelUri: String, registration: Registration): Future[\/[Error, RegistrationResponse]] = {
    findRegistrations(lastKnownChannelUri, registration).flatMap {
      case \/-(Nil) => createRegistration(registration)
      case \/-(azureRegistration :: Nil) => updateRegistration(azureRegistration, registration)
      case \/-(oneRegistration :: moreRegistrations) => deleteAndCreate(oneRegistration :: moreRegistrations, registration)
      case -\/(e: Error) => Future.successful(e.left)
    }
  }

  private def findRegistrations(lastKnownChannelUri: String, registration: Registration): Future[\/[Error, List[azure.RegistrationResponse]]] = {

    def extractResultFromResponse(
      userIdResults: HubResult[List[azure.RegistrationResponse]],
      deviceIdResults: HubResult[List[azure.RegistrationResponse]]
    ): \/[Error, List[azure.RegistrationResponse]] = {
      for {
        userIdRegistrations <- userIdResults
        deviceIdRegistrations <- deviceIdResults
      } yield (deviceIdRegistrations ++ userIdRegistrations).distinct
    }

    for {
      userIdResults <- hubClient.registrationsByTag(Tag.fromUserId(registration.userId).encodedTag)
      deviceIdResults <- hubClient.registrationsByChannelUri(channelUri = lastKnownChannelUri)
    } yield extractResultFromResponse(userIdResults, deviceIdResults)
  }

  private def createRegistration(registration: Registration): Future[\/[Error, RegistrationResponse]] = {
    logger.debug(s"creating registration $registration")
    hubClient.create(RawWindowsRegistration.fromMobileRegistration(registration))
      .map(hubResultToRegistrationResponse)
  }

  private def updateRegistration(azureRegistration: azure.RegistrationResponse, registration: Registration): Future[\/[Error, RegistrationResponse]] = {
    logger.debug(s"updating registration ${azureRegistration.registration} with $registration")
    hubClient.update(azureRegistration.registration, RawWindowsRegistration.fromMobileRegistration(registration))
      .map(hubResultToRegistrationResponse)
  }

  private def deleteAndCreate(
    registrationsToDelete: List[azure.RegistrationResponse],
    registrationToCreate: Registration): Future[\/[Error, RegistrationResponse]] = {
    deleteRegistrations(registrationsToDelete).flatMap {
      case \/-(_) => createRegistration(registrationToCreate)
      case -\/(error) => Future.successful(error.left)
    }
  }

  private def deleteRegistrations(registrations: List[azure.RegistrationResponse]): Future[\/[Error, Unit]] = {
    Future.traverse(registrations) { registration =>
      logger.debug(s"deleting registration ${registration.registration}")
      hubClient.delete(registration.registration)
    } map { responses =>
      val errors = responses.collect { case -\/(error) => error }
      if (errors.isEmpty) ().right else errors.head.left
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
