package registration.services.azure

import azure.NotificationHubClient.HubResult
import azure._
import models._
import play.api.Logger
import providers.ProviderError
import registration.services.{NotificationRegistrar, RegistrationResponse, StoredRegistration}
import tracking.{SubscriptionTracker, TopicSubscriptionTracking}

import scala.concurrent.{ExecutionContext, Future}
import cats.data.{Xor, XorT}
import cats.implicits._
import models.pagination.{Paginated, ProviderCursor}

class NotificationHubRegistrar(
  hubClient: NotificationHubClient,
  subscriptionTracker: SubscriptionTracker,
  registrationExtractor: Registration => NotificationsHubRegistration)(implicit ec: ExecutionContext)
  extends NotificationRegistrar {

  override val providerIdentifier = "azure"
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
      _ <- XorT(deleteRegistrations(registrationResponses.registrations)).ensure(UdidNotFound)(_ => registrationResponses.registrations.nonEmpty)
    } yield ()

    result.value
  }

  def findRegistrations(topic: Topic, cursor: Option[String] = None): Future[ProviderError Xor Paginated[StoredRegistration]] = {
    XorT(hubClient.registrationsByTag(Tag.fromTopic(topic).encodedTag, cursor))
      .semiflatMap(responsesToStoredRegistrations)
      .value
  }

  def findRegistrations(lastKnownChannelUri: String): Future[ProviderError Xor List[StoredRegistration]] = {
    XorT(hubClient.registrationsByChannelUri(channelUri = lastKnownChannelUri))
      .semiflatMap(responsesToStoredRegistrations)
      .value
  }

  def findRegistrations(udid: UniqueDeviceIdentifier): Future[ProviderError Xor Paginated[StoredRegistration]] = {
    XorT(hubClient.registrationsByTag(Tag.fromUserId(udid).encodedTag))
      .semiflatMap(responsesToStoredRegistrations)
      .value
  }

  private def findRegistrations(lastKnownChannelUri: String, registration: Registration): Future[ProviderError Xor List[azure.RegistrationResponse]] = {
    def extractResultFromResponse(
      userIdResults: HubResult[Registrations],
      deviceIdResults: HubResult[List[azure.RegistrationResponse]]
    ): Xor[ProviderError, List[azure.RegistrationResponse]] = {
      for {
        userIdRegistrations <- userIdResults
        deviceIdRegistrations <- deviceIdResults
      } yield (deviceIdRegistrations ++ userIdRegistrations.registrations).distinct
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

  private def deleteAndCreate(
    registrationsToDelete: List[azure.RegistrationResponse],
    registrationToCreate: Registration): RegistrarResponse[RegistrationResponse] = {
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

  private def responsesToStoredRegistrations(registrations: Registrations): Future[Paginated[StoredRegistration]] =
    responsesToStoredRegistrations(registrations.registrations).map(Paginated(_, registrations.cursor.map(ProviderCursor(providerIdentifier, _))))

  private def responsesToStoredRegistrations(responses: List[azure.RegistrationResponse]): Future[List[StoredRegistration]] = {
    Future.sequence(responses.map { response =>
      val tags = tagsIn(response)
      def topicsFromTags: Future[Set[Topic]] = Future.sequence {
        tags.topicIds.toList.map { topicId =>
          subscriptionTracker.topicFromId(topicId).map(_.toList)
        }
      } map { _.flatten.toSet }

      topicsFromTags.map { topics =>
        StoredRegistration(
          deviceId = response.deviceId,
          platform = response.platform,
          userId = tags.findUserId,
          tagIds = tags.asSet,
          topics = topics
        )
      }
    })
  }

  private def toRegistrarResponse(topicsRegisteredFor: Set[Topic])(registration: azure.RegistrationResponse): Xor[ProviderError, RegistrationResponse] = {
    val tags = tagsIn(registration)
    for {
      userId <- Xor.fromOption(tags.findUserId, UserIdNotInTags)
    } yield RegistrationResponse(
      deviceId = registration.deviceId,
      platform = registration.platform,
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
