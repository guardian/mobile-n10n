package registration.services.azure

import azure.NotificationHubClient.HubResult
import azure._
import models._
import play.api.Logger
import providers.ProviderError
import registration.services.{NotificationRegistrar, RegistrationResponse, StoredRegistration}
import tracking.{SubscriptionTracker, TopicSubscriptionTracking}

import scala.concurrent.{ExecutionContext, Future}
import cats.data.EitherT
import cats.syntax.either._
import cats.instances.future._
import com.amazonaws.services.cloudwatch.model.StandardUnit
import metrics.{MetricDataPoint, Metrics}
import models.Provider.Azure
import models.pagination.{Paginated, ProviderCursor}
import registration.services.NotificationRegistrar.RegistrarResponse

class NotificationHubRegistrar(
  val hubClient: NotificationHubClient,
  subscriptionTracker: SubscriptionTracker,
  registrationExtractor: Registration => NotificationsHubRegistration,
  metrics: Metrics
)(implicit ec: ExecutionContext)
  extends NotificationRegistrar {

  override val providerIdentifier = Provider.Azure.value
  val logger = Logger(classOf[NotificationHubRegistrar])


  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = {
    metrics.send(MetricDataPoint(name = "AzureWrite", value = 1d, unit = StandardUnit.Count))
    findRegistrationResponses(deviceToken.azureToken).flatMap {
      case Right(Nil) => createRegistration(registration)
      case Right(azureRegistration :: Nil) => updateRegistration(azureRegistration, registration)
      case Right(manyRegistrations) => deleteAndCreate(manyRegistrations, registration)
      case Left(e: ProviderError) => Future.successful(Left(e))
    }
  }

  override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = {
    metrics.send(MetricDataPoint(name = "AzureWrite", value = 1d, unit = StandardUnit.Count))
    findRegistrationResponses(deviceToken.azureToken).flatMap {
      case Right(Nil) => Future.successful(Right(()))
      case Right(manyRegistrations) => deleteRegistrations(manyRegistrations)
      case Left(e: ProviderError) => Future.successful(Left(e))
    }
  }

  def findRegistrations(topic: Topic, cursor: Option[String] = None): Future[Either[ProviderError, Paginated[StoredRegistration]]] = {
    metrics.send(MetricDataPoint(name = "AzureRead", value = 1d, unit = StandardUnit.Count))
    EitherT(hubClient.registrationsByTag(Tag.fromTopic(topic).encodedTag, cursor))
      .semiflatMap(responsesToStoredRegistrations)
      .value
  }


  override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = {
    metrics.send(MetricDataPoint(name = "AzureRead", value = 1d, unit = StandardUnit.Count))
    EitherT(hubClient.registrationsByChannelUri(channelUri = deviceToken.azureToken))
      .semiflatMap(responsesToStoredRegistrations)
      .value
  }

  def findRegistrationResponses(pushToken: String): Future[Either[ProviderError, List[azure.RegistrationResponse]]] = {
    metrics.send(MetricDataPoint(name = "AzureRead", value = 1d, unit = StandardUnit.Count))
    hubClient.registrationsByChannelUri(channelUri = pushToken).map(_.right.map(_.distinct))
  }

  def findRegistrations(udid: UniqueDeviceIdentifier): Future[Either[ProviderError, Paginated[StoredRegistration]]] = {
    EitherT(hubClient.registrationsByTag(Tag.fromUserId(udid).encodedTag))
      .semiflatMap(responsesToStoredRegistrations)
      .value
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
      case Right(_) => createRegistration(registrationToCreate)
      case Left(error) => Future.successful(Left(error))
    }
  }

  private def deleteRegistrations(registrations: List[azure.RegistrationResponse]): Future[Either[ProviderError, Unit]] = {
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
      val errors = responses.collect { case Left(error) => error }
      if (errors.isEmpty) Right(()) else Left(errors.head)
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
          tagIds = tags.asSet,
          topics = topics,
          provider = Provider.Azure.value
        )
      }
    })
  }

  private def toRegistrarResponse(topicsRegisteredFor: Set[Topic])(registration: azure.RegistrationResponse): RegistrationResponse = {
    RegistrationResponse(
      deviceId = registration.deviceId,
      platform = registration.platform,
      topics = topicsRegisteredFor,
      provider = Azure
    )
  }

  private def hubResultToRegistrationResponse(topicsRegisteredFor: Set[Topic])(hubResult: HubResult[azure.RegistrationResponse]) = {
    hubResult.map(toRegistrarResponse(topicsRegisteredFor))
  }


  private def tagsIn(registration: azure.RegistrationResponse): Tags = Tags.fromStrings(registration.tagsAsSet)

}

sealed trait WindowsNotificationProviderError extends ProviderError {
  override def providerName: String = "WNS"
}


