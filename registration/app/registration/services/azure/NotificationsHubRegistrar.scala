package registration.services.azure

import azure.NotificationHubClient.HubResult
import azure._
import models._
import play.api.Logger
import providers.ProviderError
import registration.services.{NotificationRegistrar, RegistrationResponse}
import tracking.{SubscriptionTracker, TopicSubscriptionTracking}

import scala.concurrent.{ExecutionContext, Future}
import cats.data.{Xor, XorT}
import cats.implicits._

class NotificationHubRegistrar(
  hubClient: NotificationHubClient,
  subscriptionTracker: SubscriptionTracker,
  registrationExtractor: Registration => NotificationsHubRegistration)(implicit ec: ExecutionContext)
  extends NotificationRegistrar {

  val logger = Logger(classOf[WindowsNotificationRegistrar])

  override def register(lastKnownChannelUri: String, registration: Registration): RegistrarResponse[RegistrationResponse] = {
    findRegistrations(lastKnownChannelUri, registration).flatMap {
      case Xor.Right(Nil) => createRegistration(registration)
      case Xor.Right(azureRegistration :: Nil) => updateRegistration(azureRegistration, registration)
      case Xor.Right(manyRegistrations) => deleteAndCreate(manyRegistrations, registration)
      case Xor.Left(e: ProviderError) => Future.successful(e.left)
    }
  }

  override def unregister(udid: UniqueDeviceIdentifier): RegistrarResponse[Unit] = {
    val result = for {
      registrationResponses <- XorT(hubClient.registrationsByTag(Tag.fromUserId(udid).encodedTag))
      _ <- XorT(deleteRegistrations(registrationResponses)).ensure(UdidNotFound)(_ => registrationResponses.nonEmpty)
    } yield ()

    result.value
  }

  private def findRegistrations(lastKnownChannelUri: String, registration: Registration): Future[ProviderError Xor List[azure.RegistrationResponse]] = {
    def extractResultFromResponse(
      userIdResults: HubResult[List[azure.RegistrationResponse]],
      deviceIdResults: HubResult[List[azure.RegistrationResponse]]
    ): Xor[ProviderError, List[azure.RegistrationResponse]] = {
      for {
        userIdRegistrations <- userIdResults
        deviceIdRegistrations <- deviceIdResults
      } yield (deviceIdRegistrations ++ userIdRegistrations).distinct
    }

    for {
      userIdResults <- hubClient.registrationsByTag(Tag.fromUserId(registration.udid).encodedTag)
      deviceIdResults <- hubClient.registrationsByChannelUri(channelUri = lastKnownChannelUri)
    } yield extractResultFromResponse(userIdResults, deviceIdResults)
  }

  private def createRegistration(registration: Registration): RegistrarResponse[RegistrationResponse] = {
    logger.debug(s"creating registration $registration")
    hubClient.create(registrationExtractor(registration))
      .andThen { subscriptionTracker.recordSubscriptionChange(TopicSubscriptionTracking(addedTopics = registration.topics)) }
      .map { hubResultToRegistrationResponse(registration.topics) }
  }

  private def updateRegistration(azureRegistration: azure.RegistrationResponse, registration: Registration): RegistrarResponse[RegistrationResponse] = {
    logger.debug(s"updating registration ${azureRegistration.registration} with $registration")
    hubClient.update(azureRegistration.registration, registrationExtractor(registration))
      .andThen {
        subscriptionTracker.recordSubscriptionChange(
          TopicSubscriptionTracking.withDiffBetween(
            existingTopicIds = tagsIn(azureRegistration).topicIds,
            newTopics = registration.topics
          ))
      }
      .map { hubResultToRegistrationResponse(registration.topics) }
  }

  private def deleteAndCreate(registrationsToDelete: List[azure.RegistrationResponse], registrationToCreate: Registration): RegistrarResponse[RegistrationResponse] = {
    deleteRegistrations(registrationsToDelete).flatMap {
      case Xor.Right(_) => createRegistration(registrationToCreate)
      case Xor.Left(error) => Future.successful(error.left)
    }
  }

  private def deleteRegistrations(registrations: List[azure.RegistrationResponse]): Future[ProviderError Xor Unit] = {
    Future.traverse(registrations) { registration =>
      logger.debug(s"deleting registration ${registration.registration}")
      hubClient
        .delete(registration.registration)
        .andThen {
          subscriptionTracker.recordSubscriptionChange(
            TopicSubscriptionTracking(removedTopicsIds = tagsIn(registration).topicIds)
          )
        }
    } map { responses =>
      val errors = responses.collect { case Xor.Left(error) => error }
      if (errors.isEmpty) ().right else errors.head.left
    }
  }


  private def toRegistrarResponse(topicsRegisteredFor: Set[Topic])(registration: azure.RegistrationResponse): Xor[ProviderError, RegistrationResponse] = {
    val tags = tagsIn(registration)
    val (platform, deviceId) = registration match {
      case WNSRegistrationResponse(_, _, channelUri, _) => (WindowsMobile, channelUri)
      case GCMRegistrationResponse(_, _, gcmRegistrationId, _) => (Android, gcmRegistrationId)
      case APNSRegistrationResponse(_, _, deviceToken, _) => (iOS, deviceToken)
    }
    for {
      userId <- Xor.fromOption(tags.findUserId, UserIdNotInTags)
    } yield RegistrationResponse(
      deviceId = deviceId,
      platform = platform,
      userId = userId,
      topics = topicsRegisteredFor
    )
  }

  private def hubResultToRegistrationResponse(topicsRegisteredFor: Set[Topic])(hubResult: HubResult[azure.RegistrationResponse]) = {
    hubResult.flatMap(toRegistrarResponse(topicsRegisteredFor))
  }


  private def tagsIn(registration: azure.RegistrationResponse): Tags = Tags.fromStrings(registration.tagsAsSet)

}

sealed trait WindowsNotificationProviderError extends ProviderError {
  override def providerName: String = "WNS"
}

case object UserIdNotInTags extends WindowsNotificationProviderError {
  override def reason: String = "Could not find userId in response from Hub"
}

case object UdidNotFound extends WindowsNotificationProviderError {
  override def reason: String = "Udid not found"
}
