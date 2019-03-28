package notification.services.guardian

import models.{PlatformCount, Topic, TopicCount}
import notification.data.DataStore
import play.api.libs.json.Format
import utils.LruCache

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationLong

class ReportTopicRegistrationCounter(topicCountDataStore: DataStore[TopicCount])(implicit ec: ExecutionContext) extends TopicRegistrationCounter {


  override def count(topics: List[Topic])(implicit format: Format[TopicCount] ): Future[PlatformCount]  =  {
    val topicNames = topics.map(topic => topic.fullName)
    val persitedTopicRegistrationCounts = topicCountDataStore.get()
    persitedTopicRegistrationCounts.map {
      topicCounts =>
        val totalRegistrationsForTopics = topicCounts.filter {
          topicCount => topicNames.contains(topicCount.topicName)
        }
        .map(_.registrationCount).sum
         PlatformCount(
           total = totalRegistrationsForTopics,
           ios = totalRegistrationsForTopics,
           android = totalRegistrationsForTopics,
           newsstand = totalRegistrationsForTopics)
    }
  }
}
