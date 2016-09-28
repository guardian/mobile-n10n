package tracking

import azure.NotificationHubClient.HubResult
import models.Topic
import play.api.Logger
import tracking.Repository.RepositoryResult

import scala.concurrent.Future
import scala.util.{Success, Try}
import cats.data.Xor

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionTracker(topicSubscriptionsRepository: TopicSubscriptionsRepository) {
  val logger = Logger(classOf[SubscriptionTracker])

  def recordSubscriptionChange(topicSubscriptionTracking: TopicSubscriptionTracking): PartialFunction[Try[HubResult[_]], Unit] = {
    case Success(Xor.Right(_)) =>
      Future.traverse(topicSubscriptionTracking.addedTopics) { topic =>
        logger.debug(s"Informing about new topic registrations. [$topic]")
        topicSubscriptionsRepository.deviceSubscribed(topic, 1)
      } map handleErrors
      Future.traverse(topicSubscriptionTracking.removedTopicsIds) { topicId =>
        logger.debug(s"Informing about removed topic registrations. [$topicId]")
        topicSubscriptionsRepository.deviceUnsubscribed(topicId, 1)
      } map handleErrors

    case _ => logger.error("Topic subscription counters not updated. Preceding action failed.")
  }

  private def handleErrors(responses: Set[RepositoryResult[Unit]]): Unit = {
    val errors = responses.filter(_.isLeft)
    if (errors.nonEmpty) logger.error("Failed saving topic subscriptions: " + errors.mkString(","))
  }
}

case class TopicSubscriptionTracking(addedTopics: Set[Topic] = Set.empty, removedTopicsIds: Set[String] = Set.empty)


object TopicSubscriptionTracking {
  type TopicId = String

  def withDiffBetween(existingTopicIds: Set[TopicId], newTopics: Set[Topic]): TopicSubscriptionTracking = {
    val newTopicIds = topicIds(newTopics).diff(existingTopicIds)
    TopicSubscriptionTracking(
      addedTopics = newTopics.filter { t => newTopicIds.contains(t.id) },
      removedTopicsIds = existingTopicIds.diff(topicIds(newTopics))
    )
  }

  private def topicIds(topics: Set[Topic]): Set[TopicId] = topics map {_.id}
}
