package registration.services.topic

import models.Topic
import registration.auditor.AuditorGroup
import registration.services.Configuration

import scala.concurrent.{ExecutionContext, Future}

trait TopicValidator {
  def removeInvalid(topics: Set[Topic]): Future[Either[TopicValidatorError, Set[Topic]]]
}

trait TopicValidatorError {
  def reason: String

  def topicsQueried: Set[Topic]
}

final class AuditorTopicValidator(configuration: Configuration, auditors: AuditorGroup)(implicit ec: ExecutionContext)
  extends TopicValidator {

  override def removeInvalid(topics: Set[Topic]): Future[Either[TopicValidatorError, Set[Topic]]] =
    auditors
      .queryEach { _.expiredTopics(topics) }
      .map(expired => topics -- expired.flatten)
      .map(limitTopics(configuration.maxTopics))
      .map(Right.apply)
      .recover {
        case e: Throwable => Left(AuditorClientError(e.getMessage, topics))
      }

  private def limitTopics(maxTopics: Int)(topics: Set[Topic]): Set[Topic] =
    topics.toList
      .sortWith(_.`type`.priority > _.`type`.priority)
      .take(maxTopics)
      .toSet

  case class AuditorClientError(reason: String, topicsQueried: Set[Topic]) extends TopicValidatorError
}

