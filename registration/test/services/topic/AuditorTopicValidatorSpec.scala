package services.topic

import java.net.URL

import auditor.{Auditor, AuditorGroupConfig, AuditorWSClient}
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
      val validInB = Topic(`type` = Content, name = "validInB")
      val topics = Set(
        invalidTopic,
        validInA,
        validInB
      )
      auditorWSClient.expiredTopics(argThat(===(auditorA)), anySetOf[Topic]) returns successful(topics - invalidTopic - validInB)
      auditorWSClient.expiredTopics(argThat(===(auditorB)), anySetOf[Topic]) returns successful(topics - invalidTopic - validInA)

      val validTopics = topicValidator.removeInvalid(topics)

      validTopics must beEqualTo((topics - invalidTopic).right).await
    }
  }

  trait validators extends Scope {
    val auditorWSClient = mock[AuditorWSClient]
    val auditorA = Auditor(new URL("http://localhost/auditorA"))
    val auditorB = Auditor(new URL("http://locahost/auditorB"))
    val topicValidator = {
      val configuration = new Configuration() {
        override lazy val auditorConfiguration = AuditorGroupConfig(
          hosts = Set(auditorA.host, auditorB.host).map(_.toString)
        )
      }
      new AuditorTopicValidator(auditorWSClient, configuration)
    }
  }
}
