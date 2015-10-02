package tracking

import models.Topic
import tracking.Repository.RepositoryResult
import scala.concurrent.Future

trait TopicSubscriptionsRepository {

  def subscribe(topic: Topic): Future[RepositoryResult[Unit]]

  def unsubscribe(topic: Topic): Future[RepositoryResult[Unit]]

  def count(topic: Topic): Future[RepositoryResult[Int]]
}
