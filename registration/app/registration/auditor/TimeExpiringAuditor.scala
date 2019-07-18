package registration.auditor

import models.Topic
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

case class TimeExpiringAuditor(referenceTopics: Set[Topic], expiry: DateTime) extends Auditor {
  override def expiredTopics(topics: Set[Topic])(implicit ec: ExecutionContext): Future[Set[Topic]] = {
    Future.successful {
      if (DateTime.now.isAfter(expiry))
        topics.intersect(referenceTopics)
      else
        Set.empty
    }
  }
}