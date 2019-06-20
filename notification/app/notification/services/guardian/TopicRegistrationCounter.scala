package notification.services.guardian

import models.{Topic, TopicCount}
import notification.data.DataStore

import scala.concurrent.{ExecutionContext, Future}

trait TopicRegistrationCounter {
  def count(topics: List[Topic]): Future[Int]
}

class TopicRegistrationCounterImpl(topicCountDataStore: DataStore[TopicCount])(implicit ec: ExecutionContext) extends TopicRegistrationCounter {

  override def count(topics: List[Topic]): Future[Int]  =  {
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
