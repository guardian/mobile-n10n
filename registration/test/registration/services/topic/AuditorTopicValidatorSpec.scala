package registration.services.topic

import java.net.URL

import auditor.{Auditor, AuditorGroupConfig, AuditorWSClient}
import models.Topic
import models.TopicTypes.{Content, FootballMatch}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import registration.services.Configuration

import scala.concurrent.Future.successful
import cats.implicits._

class AuditorTopicValidatorSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
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
