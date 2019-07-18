package registration.auditor

import scala.concurrent.{ExecutionContext, Future}

case class AuditorGroup(auditors: Set[Auditor]) {
  def queryEach[T](query: Auditor => Future[T])(implicit ec: ExecutionContext): Future[Set[T]] =
    Future.sequence(auditors.map(query))
}