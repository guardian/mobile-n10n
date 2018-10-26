package notification.services.guardian

import models.{PlatformCount, Topic}

import scala.concurrent.Future

trait TopicRegistrationCounter {
  def count(topics: List[Topic]): Future[PlatformCount]
}
