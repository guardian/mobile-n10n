package registration.services.topic

import models.{Topic, TopicTypes}
import models.TopicTypes.{Content, FootballMatch}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.matcher.EitherMatchers
import registration.services.Configuration
import play.api.{Configuration => PlayConfig}

import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext
import cats.implicits._
import registration.auditor.{Auditor, AuditorApiConfig, AuditorGroup, AuditorGroupConfig}

class AuditorTopicValidatorSpec(implicit ee: ExecutionEnv) extends Specification with Mockito with EitherMatchers {
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
      auditorA.expiredTopics(anySetOf[Topic])(any[ExecutionContext]) returns successful(Set(expiredInA))
      auditorB.expiredTopics(anySetOf[Topic])(any[ExecutionContext]) returns successful(Set(expiredInB))

      val validTopics = topicValidator.removeInvalid(topics)

      validTopics must beEqualTo(Right(Set(validTopic))).await
    }

    "Limit the number of tags to 200" in new validators {
      auditorA.expiredTopics(anySetOf[Topic])(any[ExecutionContext]) returns successful(Set.empty)
      auditorB.expiredTopics(anySetOf[Topic])(any[ExecutionContext]) returns successful(Set.empty)

      val topics = for {
        i <- 0 to 300
      } yield Topic(TopicTypes.Content, s"test-$i")

      val validTopics = topicValidator.removeInvalid(topics.toSet)

      validTopics must beRight(haveSize[Set[Topic]](testMaxTopics)).await
    }

    "Do not filter breaking news if topic list too long" in new validators {
      auditorA.expiredTopics(anySetOf[Topic])(any[ExecutionContext]) returns successful(Set.empty)
      auditorB.expiredTopics(anySetOf[Topic])(any[ExecutionContext]) returns successful(Set.empty)
      val topics = for {
        i <- 0 to 300
      } yield Topic(TopicTypes.Content, s"test-$i")

      val breakingNewsTopic = Topic(TopicTypes.Breaking, "uk")
      val validTopics = topicValidator.removeInvalid(topics.toSet + breakingNewsTopic)

      validTopics must beRight(contain(breakingNewsTopic)).await
    }
  }

  trait validators extends Scope {

    val auditorA = mock[Auditor]
    val auditorB = mock[Auditor]
    val testMaxTopics = 200
    val topicValidator = {
      val configuration = new Configuration(PlayConfig.empty) {
        override lazy val auditorConfiguration = AuditorGroupConfig(
          contentApiConfig = AuditorApiConfig(apiKey = "test", url = "test"),
          paApiConfig = AuditorApiConfig(apiKey = "test", url = "test")
        )
        override lazy val maxTopics = testMaxTopics
      }
      new AuditorTopicValidator(configuration, AuditorGroup(Set(auditorA, auditorB)))
    }
  }
}
