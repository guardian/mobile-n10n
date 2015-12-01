package auditor

import models.Topic
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class AuditorWSClient(wsClient: WSClient)(implicit ec: ExecutionContext) {
  val logger = Logger(classOf[AuditorWSClient])

  def expiredTopics(auditor: Auditor, topics: Set[Topic]): Future[Set[Topic]] = topics.toList match {
    case Nil => Future.successful(topics)
    case tl => logger.debug(s"Asking auditor ($auditor) for expired topics with $topics")
      wsClient
      .url(s"${ auditor.host }/expired-topics")
      .post(Json.toJson(ExpiredTopicsRequest(tl)))
      .map { _.json.as[ExpiredTopicsResponse].topics.toSet }
  }
}
