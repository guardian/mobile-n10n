package notification.services.guardian
import models.{PlatformCount, Topic}
import play.api.libs.ws.WSClient
import utils.LruCache

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationLong

class ReportTopicRegistrationCounter(
  ws: WSClient,
  reportUrl: String,
  apiKey: String
)(implicit ec: ExecutionContext) extends TopicRegistrationCounter {

  val lruCache: LruCache[PlatformCount] = new LruCache[PlatformCount](200, 1000, 3.days)

  override def count(topics: List[Topic]): Future[PlatformCount] = {
    lruCache(topics.toSet) {
      val topicParameters = topics.map(topic => "topics" -> topic.toString)
      val allParams = topicParameters :+ "api-key" -> apiKey
      ws.url(s"$reportUrl/registration-count")
        .withQueryStringParameters(allParams: _*)
        .get
        .map(response => response.json.as[PlatformCount])
    }
  }
}
