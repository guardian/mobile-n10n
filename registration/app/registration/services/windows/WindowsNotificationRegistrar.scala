package registration.services.windows

import azure.NotificationHubClient.HubResult
import azure.{NotificationHubClient, RawWindowsRegistration, Tag, Tags}
import models._
import play.api.Logger
import providers.ProviderError
import registration.services.{NotificationRegistrar, RegistrationResponse}
import tracking.Repository._
import tracking.TopicSubscriptionsRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success}
import scalaz.std.option.optionSyntax._
import scalaz.syntax.either._
import scalaz.{-\/, \/, \/-}

class WindowsNotificationRegistrar(hubClient: NotificationHubClient, topicSubscriptionsRepository: TopicSubscriptionsRepository)(implicit ec: ExecutionContext)
  extends NotificationRegistrar {

  val logger = Logger(classOf[WindowsNotificationRegistrar])

  override def register(lastKnownChannelUri: String, registration: Registration): RegistrarResponse = {
    findRegistrations(lastKnownChannelUri, registration).flatMap {
      case \/-(Nil) => createRegistration(registration)
      case \/-(azureRegistration :: Nil) => updateRegistration(azureRegistration, registration)
      case \/-(manyRegistrations) => deleteAndCreate(manyRegistrations, registration)
      case -\/(e: ProviderError) => Future.successful(e.left)
    }
  }

  private def findRegistrations(lastKnownChannelUri: String, registration: Registration): Future[ProviderError \/ List[azure.RegistrationResponse]] = {
    def extractResultFromResponse(
      userIdResults: HubResult[List[azure.RegistrationResponse]],
      deviceIdResults: HubResult[List[azure.RegistrationResponse]]
    ): \/[ProviderError, List[azure.RegistrationResponse]] = {
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

  private def createRegistration(registration: Registration): RegistrarResponse = {
    logger.debug(s"creating registration $registration")
    hubClient.create(RawWindowsRegistration.fromMobileRegistration(registration))
      .andThen { recordTopicSubscriptions(added = registration.topics) }
      .map(hubResultToRegistrationResponse)
  }

  private def updateRegistration(azureRegistration: azure.RegistrationResponse, registration: Registration): RegistrarResponse = {
    logger.debug(s"updating registration ${azureRegistration.registration} with $registration")
    hubClient.update(azureRegistration.registration, RawWindowsRegistration.fromMobileRegistration(registration))
      .andThen {
        val existingRegistrationTopics = tagsIn(azureRegistration).decodedTopics
        val newRegistrationTopics = registration.topics
        recordTopicSubscriptions(
          added = newRegistrationTopics.diff(existingRegistrationTopics),
          removed = existingRegistrationTopics.diff(newRegistrationTopics)
        )}
      .map(hubResultToRegistrationResponse)
  }

  private def deleteAndCreate(registrationsToDelete: List[azure.RegistrationResponse], registrationToCreate: Registration): RegistrarResponse = {
    deleteRegistrations(registrationsToDelete).flatMap {
      case \/-(_) => createRegistration(registrationToCreate)
      case -\/(error) => Future.successful(error.left)
    }
  }

  private def deleteRegistrations(registrations: List[azure.RegistrationResponse]): Future[ProviderError \/ Unit] = {
    Future.traverse(registrations) { registration =>
      logger.debug(s"deleting registration ${registration.registration}")
      hubClient
        .delete(registration.registration)
        .andThen {
          val removedTopics = registrations.map(tagsIn).flatMap(_.decodedTopics).toSet
          recordTopicSubscriptions(removed = removedTopics)
        }
    } map { responses =>
      val errors = responses.collect { case -\/(error) => error }
      if (errors.isEmpty) ().right else errors.head.left
    }
  }

  private def hubResultToRegistrationResponse(hubResult: HubResult[azure.RegistrationResponse]) =
    hubResult.flatMap(toRegistrarResponse)

  private def toRegistrarResponse(registration: azure.RegistrationResponse): UserIdNotInTags \/ RegistrationResponse = {
    val tags = tagsIn(registration)
    for {
      userId <- tags.findUserId \/> UserIdNotInTags()
    } yield RegistrationResponse(
      deviceId = registration.channelUri,
      platform = WindowsMobile,
      userId = userId,
      topics = tags.decodedTopics
    )
  }

  private def tagsIn(registration: azure.RegistrationResponse): Tags = Tags.fromStrings(registration.tagsAsSet)

  private def recordTopicSubscriptions(added: Set[Topic] = Set.empty, removed: Set[Topic] = Set.empty): PartialFunction[Try[HubResult[_]], Unit] = {
    case Success(\/-(_)) =>
      Future.traverse(added) { t =>
        logger.debug(s"Informing about new topic registrations. [$added]")
        topicSubscriptionsRepository.deviceSubscribed(t)
      } map handleErrors
      Future.traverse(removed) { t =>
        logger.debug(s"Informing about removed topic registrations. [$removed]")
        topicSubscriptionsRepository.deviceUnsubscribed(t)
      } map handleErrors

    case _ => logger.error("Topic subscription counters not updated. Preceding action failed.")
  }

  private def handleErrors(responses: Set[RepositoryResult[Unit]]): Unit = {
    val errors = responses.filter(_.isLeft)
    if (errors.nonEmpty) logger.error("Failed saving topic subscriptions: " + errors.mkString(","))
  }
}

sealed trait WindowsNotificationProviderError extends ProviderError {
  override def providerName: String = "WNS"
}

case class UserIdNotInTags() extends WindowsNotificationProviderError {
  override def reason: String = "Could not find userId in response from Hub"
}
