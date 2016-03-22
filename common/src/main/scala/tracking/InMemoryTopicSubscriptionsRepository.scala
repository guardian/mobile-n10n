package tracking

import models.Topic
import tracking.Repository._

import scala.concurrent.Future

class InMemoryTopicSubscriptionsRepository extends TopicSubscriptionsRepository {

  val counters = scala.collection.mutable.Map.empty[Topic, Int]

  override def deviceSubscribed(topic: Topic): Future[RepositoryResult[Unit]] = {
    counters.update(topic, counters.getOrElse(topic, 0) + 1)
    Future.successful(RepositoryResult(()))
  }

  override def deviceUnsubscribed(topic: Topic): Future[RepositoryResult[Unit]] = {
    counters.update(topic, counters.getOrElse(topic, 0) + 1)
    Future.successful(RepositoryResult(()))
  }

  override def count(topic: Topic): Future[RepositoryResult[Int]] =
    Future.successful(RepositoryResult(counters.getOrElse(topic, 0)))
}
