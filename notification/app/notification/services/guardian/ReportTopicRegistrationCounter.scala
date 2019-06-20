package notification.services.guardian

import models.{Topic, TopicCount}
import notification.data.DataStore
import play.api.libs.json.Format

import scala.concurrent.{ExecutionContext, Future}

class ReportTopicRegistrationCounter(topicCountDataStore: DataStore[TopicCount])(implicit ec: ExecutionContext) extends TopicRegistrationCounter {

  override def count(topics: List[Topic])(implicit format: Format[TopicCount] ): Future[Int]  =  {
    val topicNames = topics.map(topic => topic.fullName)
    val persitedTopicRegistrationCounts = topicCountDataStore.get()
    persitedTopicRegistrationCounts.map {
      topicCounts =>
        topicCounts.filter {
          topicCount => topicNames.contains(topicCount.topicName)
        }.map(_.registrationCount).sum
    }
  }
}
