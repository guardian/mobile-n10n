package notification.services.guardian

import models.{PlatformCount, Topic, TopicCount}
import play.api.libs.json.Format

import scala.concurrent.Future

trait TopicRegistrationCounter {
  def count(topics: List[Topic])(implicit format: Format[TopicCount]): Future[PlatformCount]
}
