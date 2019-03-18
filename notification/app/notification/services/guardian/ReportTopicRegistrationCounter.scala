package notification.services.guardian

import models.{PlatformCount, Topic, TopicCount}
import notification.data.DataStore
import play.api.libs.json.Format
import utils.LruCache

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationLong

class ReportTopicRegistrationCounter(topicCountDataStore: DataStore[TopicCount])(implicit ec: ExecutionContext) extends TopicRegistrationCounter {

  val lruCache: LruCache[PlatformCount] = new LruCache[PlatformCount](200, 1000, 3.days)

  override def count(topics: List[Topic])(implicit format: Format[TopicCount] ): Future[PlatformCount]  =  {
    lruCache(topics.toSet) {
      val topicNames = topics.map(topic => topic.fullName)
      val persitedTopicRegistrationCounts = topicCountDataStore.get()
      persitedTopicRegistrationCounts.map {
        top =>
          val totalRegistrationsForTopics = top.filter{
            topicCount => topicNames.contains(topicCount.topicName)
          }
          .foldRight(0){ (topic, count) => count + topic.registrationCount }
         PlatformCount(total = totalRegistrationsForTopics, ios = 0, android = 0, newsstand = 0)
      }
    }
  }
}
