package auditor

import models.Topic
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class AuditorWSClient(wsClient: WSClient)(implicit ec: ExecutionContext) {
  def expiredTopics(auditor: Auditor, topics: Set[Topic]): Future[Set[Topic]] = topics.toList match {
    case Nil => Future.successful(topics)
    case tl => wsClient
      .url(s"${ auditor.host }/expired-topics")
      .post(Json.toJson(ExpiredTopicsRequest(tl)))
      .map { _.json.as[ExpiredTopicsResponse].topics.toSet }
  }
}
