package tracking

import models.Topic
import tracking.Repository.RepositoryResult
import scala.concurrent.Future

trait TopicSubscriptionsRepository {
  def deviceSubscribed(topic: Topic): Future[RepositoryResult[Unit]]

  def deviceUnsubscribed(topicId: String): Future[RepositoryResult[Unit]]

  def count(topic: Topic): Future[RepositoryResult[Int]]
}
