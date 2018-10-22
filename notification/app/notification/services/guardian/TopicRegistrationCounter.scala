package notification.services.guardian

import models.Topic

import scala.concurrent.Future

trait TopicRegistrationCounter {
  def count(topics: List[Topic]): Future[TopicStats]
}
