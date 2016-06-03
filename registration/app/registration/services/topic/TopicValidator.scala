package registration.services.topic


import auditor.{AuditorWSClient, mkAuditorGroup}
import models.Topic
import registration.services.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

trait TopicValidator {
  def removeInvalid(topics: Set[Topic]): Future[TopicValidatorError \/ Set[Topic]]
}

trait TopicValidatorError {
  def reason: String

  def topicsQueried: Set[Topic]
}

final class AuditorTopicValidator(auditorClient: AuditorWSClient, configuration: Configuration)
  extends TopicValidator {

  override def removeInvalid(topics: Set[Topic]): Future[TopicValidatorError \/ Set[Topic]] =
    mkAuditorGroup(configuration.auditorConfiguration)
      .queryEach { auditorClient.expiredTopics(_, topics) }
      .map { expired => (topics -- expired.flatten).right }
      .recover {
        case e: Throwable => AuditorClientError(e.getMessage, topics).left
      }
  
  case class AuditorClientError(reason: String, topicsQueried: Set[Topic]) extends TopicValidatorError
}

