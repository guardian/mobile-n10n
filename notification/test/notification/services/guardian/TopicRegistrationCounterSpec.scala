package notification.services.guardian

import models.TopicTypes.Breaking
import models.{Topic, TopicCount}
import notification.data.DataStore
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Format

import scala.concurrent.{ExecutionContext, Future}

class TopicRegistrationCounterSpec(implicit ee: ExecutionEnv) extends Specification {

  "The topic registration counter" should {
    "Count single topics" in new TopicRegistrationScope {
      val topicCounter = topicRegistrationCounter(List(TopicCount("breaking/uk", 12345), TopicCount("breaking/us", 1)))

      topicCounter.count(List(Topic(Breaking, "uk"))) should beSome(12345).await
    }
    "Count multiple topics" in new TopicRegistrationScope {
      val topicCounter = topicRegistrationCounter(List(TopicCount("breaking/uk", 12345), TopicCount("breaking/us", 1)))

      topicCounter.count(List(Topic(Breaking, "uk"),Topic(Breaking, "us"))) should beSome(12346).await
    }
    "Not count topics that aren't in the data store" in new TopicRegistrationScope {
      val topicCounter = topicRegistrationCounter(List(TopicCount("breaking/uk", 12345), TopicCount("breaking/us", 1)))

      topicCounter.count(List(Topic(Breaking, "au"))) should beNone.await
    }
  }

  trait TopicRegistrationScope extends Scope {
    def mockDataStore(topicCounts: List[TopicCount]): DataStore[TopicCount] = new DataStore[TopicCount] {
      override def get()(implicit executionContext: ExecutionContext, format: Format[TopicCount]): Future[List[TopicCount]] =
        Future.successful(topicCounts)
    }

    def topicRegistrationCounter(topicCounts: List[TopicCount]) = new TopicRegistrationCounterImpl(mockDataStore(topicCounts))
  }
}
