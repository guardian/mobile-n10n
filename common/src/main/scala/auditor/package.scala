import java.net.URL

import models.TopicTypes.ElectionResults
import models.{Topic, TopicTypes}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

package object auditor {

  case class ContentApiConfig(url: String, apiKey: String)

  case class AuditorGroupConfig(hosts: Set[String], contentApiConfig: ContentApiConfig)

  sealed trait Auditor {
    def expiredTopics(topics: Set[Topic])(implicit ec: ExecutionContext): Future[Set[Topic]]
  }

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

  case class LiveblogAuditor(wsClient: WSClient, config: ContentApiConfig) extends Auditor {

    override def expiredTopics(topics: Set[Topic])(implicit ec: ExecutionContext): Future[Set[Topic]] = {
      val contentTopics = topics.filter(_.`type` == TopicTypes.Content)

      Future.traverse(contentTopics) { topic =>
        isLiveBloggingNow(topic.name).map {
          case true => None
          case false => Some(topic)
        }
      }.map(_.flatten)
    }

    def isLiveBloggingNow(id: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      val url = s"${config.url}/$id?show-fields=liveBloggingNow&api-key=${config.apiKey}"
      wsClient.url(url).get().map { response =>
        val liveBloggingNowField = response.json \ "response" \ "content" \ "fields" \ "liveBloggingNow"
        liveBloggingNowField.validate[String].asOpt.flatMap(s => Try(s.toBoolean).toOption).getOrElse(false)
      } recover { case _ => true }
    }
  }

  case class RemoteAuditor(host: URL, wsClient: WSClient) extends Auditor {
    val logger = Logger(classOf[RemoteAuditor])

    def expiredTopics(topics: Set[Topic])(implicit ec: ExecutionContext): Future[Set[Topic]] = topics.toList.filterNot(_.`type` == ElectionResults) match {
      case Nil => Future.successful(topics)
      case tl => logger.debug(s"Asking auditor ($host) for expired topics with $topics")
        wsClient
          .url(s"$host/expired-topics")
          .post(Json.toJson(ExpiredTopicsRequest(tl)))
          .map { _.json.as[ExpiredTopicsResponse].topics.toSet }
    }
  }

  case class AuditorGroup(auditors: Set[Auditor]) {
    def queryEach[T](query: Auditor => Future[T])(implicit ec: ExecutionContext): Future[Set[T]] =
      Future.sequence(auditors.map(query))
  }

  case class ExpiredTopicsRequest(topics: List[Topic])
  case class ExpiredTopicsResponse(topics: List[Topic])

  implicit val expiredTopicsRequestFormat = Json.format[ExpiredTopicsRequest]
  implicit val expiredTopicsResponseFormat = Json.format[ExpiredTopicsResponse]
}
