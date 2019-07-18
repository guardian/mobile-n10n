package registration.auditor

import models.Topic

import scala.concurrent.{ExecutionContext, Future}

trait Auditor {
  def expiredTopics(topics: Set[Topic])(implicit ec: ExecutionContext): Future[Set[Topic]]
}
