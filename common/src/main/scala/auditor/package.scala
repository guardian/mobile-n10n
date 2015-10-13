import java.net.URL

import models.Topic
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

package object auditor {

  case class AuditorGroupConfig(hosts: Set[String])

  case class Auditor(host: URL)

  case class AuditorGroup(auditors: Set[Auditor]) {
    def queryEach[T](query: Auditor => Future[T])(implicit ec: ExecutionContext): Future[Set[T]] =
      Future.sequence(auditors.map(query))
  }

  def mkAuditorGroup(config: AuditorGroupConfig): AuditorGroup = AuditorGroup(
    config.hosts map { host => Auditor(new URL(host)) }
  )


  case class ExpiredTopicsRequest(topics: List[Topic])
  case class ExpiredTopicsResponse(topics: List[Topic])

  implicit val expiredTopicsRequestFormat = Json.format[ExpiredTopicsRequest]
  implicit val expiredTopicsResponseFormat = Json.format[ExpiredTopicsResponse]
}
