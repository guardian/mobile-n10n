package tracking

import cats.data.Xor
import models.Topic
import tracking.Repository._

import scala.concurrent.Future

class InMemoryTopicSubscriptionsRepository extends TopicSubscriptionsRepository {

  val counters = scala.collection.mutable.Map.empty[Topic, Int]

  override def deviceSubscribed(topic: Topic): Future[RepositoryResult[Unit]] = {
    counters.update(topic, counters.getOrElse(topic, 0) + 1)
    Future.successful(RepositoryResult(()))
  }

  override def count(topic: Topic): Future[RepositoryResult[Int]] =
    Future.successful(RepositoryResult(counters.getOrElse(topic, 0)))

  override def deviceUnsubscribed(topicId: String): Future[RepositoryResult[Unit]] = {
    counters.transform { case (topic, count) =>
      if (topic.id == topicId) count - 1 else count
    }
    Future.successful(RepositoryResult(()))
  }

  override def topicFromId(topicId: String): Future[RepositoryResult[Topic]] =
    Future.successful(Xor.fromOption(counters.keys.find(_.id == topicId), RepositoryError("Topic not found")))
}
