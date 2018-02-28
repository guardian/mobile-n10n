package tracking

import models.Topic
import tracking.Repository._
import cats.syntax.either._

import scala.concurrent.Future

class InMemoryTopicSubscriptionsRepository extends TopicSubscriptionsRepository {

  val counters = scala.collection.mutable.Map.empty[Topic, Int]

  override def deviceSubscribed(topic: Topic, count: Int = 1): Future[RepositoryResult[Unit]] = {
    counters.update(topic, counters.getOrElse(topic, 0) + count)
    Future.successful(RepositoryResult(()))
  }

  override def count(topic: Topic): Future[RepositoryResult[Int]] =
    Future.successful(RepositoryResult(counters.getOrElse(topic, 0)))

  override def deviceUnsubscribed(topicId: String, count: Int): Future[RepositoryResult[Unit]] = {
    counters.transform { case (topic, existingCount) =>
      if (topic.id == topicId) existingCount - count else existingCount
    }
    Future.successful(RepositoryResult(()))
  }

  override def topicFromId(topicId: String): Future[RepositoryResult[Topic]] =
    Future.successful(Either.fromOption(counters.keys.find(_.id == topicId), RepositoryError("Topic not found")))
}
