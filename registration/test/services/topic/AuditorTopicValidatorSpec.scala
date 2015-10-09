package services.topic

import auditor.{AuditorGroupConfig, AuditorWSClient}
import models.Topic
import models.TopicTypes.{Content, FootballMatch}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import services.Configuration

import scala.concurrent.Future.successful
import scalaz.syntax.either._

class AuditorTopicValidatorSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "Auditor Topic Validator" should {
    "return filtered list of topics from both auditors" in new validators {
      val invalidTopic = Topic(`type` = FootballMatch, name = "invalidFromAuditorA")
      val validInA = Topic(`type` = Content, name = "validinA")
      val topics = Set(
        invalidTopic,
        validInA,
        Topic(`type` = Content, name = "validInB")
      )
      auditorWSClient.expiredTopics(any, anySetOf[Topic]) returns successful(topics - invalidTopic) thenReturns successful(topics - invalidTopic - validInA)

      val validTopics = topicValidator.removeInvalid(topics)

      validTopics must beEqualTo((topics - invalidTopic).right).await
    }
  }

  trait validators extends Scope {
    val configuration = new Configuration() {
      override lazy val auditorConfiguration = AuditorGroupConfig(
        hosts = Set("http://localhost/auditorA", "http://locahost/auditorB")
      )
    }
    val auditorWSClient = mock[AuditorWSClient]
    val topicValidator = new AuditorTopicValidator(auditorWSClient, configuration)
  }
}
