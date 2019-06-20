package notification.services.guardian

import models.{Topic, TopicCount}
import notification.data.DataStore

import scala.concurrent.{ExecutionContext, Future}

trait TopicRegistrationCounter {
  def count(topics: List[Topic]): Future[Option[Int]]
}

class TopicRegistrationCounterImpl(topicCountDataStore: DataStore[TopicCount])(implicit ec: ExecutionContext) extends TopicRegistrationCounter {

  override def count(topics: List[Topic]): Future[Option[Int]]  =  {
    val topicNames = topics.map(topic => topic.fullName)
    topicCountDataStore.get().map { topicCounts =>
      val counts = topicCounts.filter {
        topicCount => topicNames.contains(topicCount.topicName)
      }.map(_.registrationCount)

      counts.foldLeft(Option.empty[Int]) {
        case (None, value) => Some(value)
        case (Some(sum), value) => Some(sum + value)
      }
    }
  }
}
