package registration.services.topic


import auditor.{AuditorWSClient, mkAuditorGroup}
import models.Topic
import registration.services.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import cats.data.Xor
import cats.implicits._

trait TopicValidator {
  def removeInvalid(topics: Set[Topic]): Future[TopicValidatorError Xor Set[Topic]]
}

trait TopicValidatorError {
  def reason: String

  def topicsQueried: Set[Topic]
}

final class AuditorTopicValidator(auditorClient: AuditorWSClient, configuration: Configuration)
  extends TopicValidator {

  override def removeInvalid(topics: Set[Topic]): Future[TopicValidatorError Xor Set[Topic]] =
    mkAuditorGroup(configuration.auditorConfiguration)
      .queryEach { auditorClient.expiredTopics(_, topics) }
      .map(expired => topics -- expired.flatten)
      .map(limitTopics(configuration.maxTopics))
      .map(_.right)
      .recover {
        case e: Throwable => AuditorClientError(e.getMessage, topics).left
      }

  private def limitTopics(maxTopics: Int)(topics: Set[Topic]): Set[Topic] =
    topics.toList
      .sortWith(_.`type`.priority > _.`type`.priority)
      .take(maxTopics)
      .toSet

  case class AuditorClientError(reason: String, topicsQueried: Set[Topic]) extends TopicValidatorError
}

