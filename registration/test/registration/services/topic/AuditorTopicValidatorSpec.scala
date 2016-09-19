package registration.services.topic

import java.net.URL

import auditor.{Auditor, AuditorGroupConfig, AuditorWSClient}
import models.{Topic, TopicTypes}
import models.TopicTypes.{Content, FootballMatch}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.matcher.XorMatchers
import registration.services.Configuration

import scala.concurrent.Future.successful
import cats.implicits._

class AuditorTopicValidatorSpec(implicit ee: ExecutionEnv) extends Specification with Mockito with XorMatchers {
  "Auditor Topic Validator" should {
    "return filtered list of topics from both auditors" in new validators {
      val validTopic = Topic(`type` = FootballMatch, name = "invalidFromAuditorA")
      val expiredInA = Topic(`type` = Content, name = "expiredInA")
      val expiredInB = Topic(`type` = Content, name = "expiredInB")
      val topics = Set(
        validTopic,
        expiredInA,
        expiredInB
      )
      auditorWSClient.expiredTopics(===(auditorA), anySetOf[Topic]) returns successful(Set(expiredInB))
      auditorWSClient.expiredTopics(===(auditorB), anySetOf[Topic]) returns successful(Set(expiredInA))

      val validTopics = topicValidator.removeInvalid(topics)

      validTopics must beEqualTo(Set(validTopic).right).await
    }

    "Limit the number of tags to 200" in new validators {
      auditorWSClient.expiredTopics(===(auditorA), anySetOf[Topic]) returns successful(Set.empty)
      auditorWSClient.expiredTopics(===(auditorB), anySetOf[Topic]) returns successful(Set.empty)

      val topics = for {
        i <- 0 to 300
      } yield Topic(TopicTypes.Content, s"test-$i")

      val validTopics = topicValidator.removeInvalid(topics.toSet)

      validTopics must beXorRight(haveSize[Set[Topic]](testMaxTopics)).await
    }

    "Do not filter breaking news if topic list too long" in new validators {
      auditorWSClient.expiredTopics(===(auditorA), anySetOf[Topic]) returns successful(Set.empty)
      auditorWSClient.expiredTopics(===(auditorB), anySetOf[Topic]) returns successful(Set.empty)

      val topics = for {
        i <- 0 to 300
      } yield Topic(TopicTypes.Content, s"test-$i")

      val breakingNewsTopic = Topic(TopicTypes.Breaking, "uk")
      val validTopics = topicValidator.removeInvalid(topics.toSet + breakingNewsTopic)

      validTopics must beXorRight(contain(breakingNewsTopic)).await
    }
  }

  trait validators extends Scope {
    val auditorWSClient = mock[AuditorWSClient]
    val auditorA = Auditor(new URL("http://localhost/auditorA"))
    val auditorB = Auditor(new URL("http://locahost/auditorB"))
    val testMaxTopics = 200
    val topicValidator = {
      val configuration = new Configuration() {
        override lazy val auditorConfiguration = AuditorGroupConfig(
          hosts = Set(auditorA.host, auditorB.host).map(_.toString)
        )
        override lazy val maxTopics = testMaxTopics
      }
      new AuditorTopicValidator(auditorWSClient, configuration)
    }
  }
}
