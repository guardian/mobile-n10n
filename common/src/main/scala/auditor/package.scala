import models.{Topic, TopicTypes}
import org.joda.time.DateTime
import pa.PaClient
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import spray.caching.{Cache, LruCache}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.concurrent.duration.DurationInt

package object auditor {

  case class ApiConfig(url: String, apiKey: String)

  case class AuditorGroupConfig(contentApiConfig: ApiConfig, paApiConfig: ApiConfig)

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

  case class LiveblogAuditor(wsClient: WSClient, config: ApiConfig) extends Auditor {

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

  case class FootballMatchAuditor(client: PaClient)(implicit ec: ExecutionContext) extends Auditor {

    private val matchStatusCache: Cache[String] = LruCache[String](timeToLive = 5 minutes)

    private val matchEndedStatuses = List(
      "FT",
      "PTFT",
      "Result",
      "ETFT",
      "MC",
      "Abandoned",
      "Cancelled"
    )

    override def expiredTopics(topics: Set[Topic])(implicit ec: ExecutionContext): Future[Set[Topic]] = {
      val footballMatchTopics =  topics.filter(_.`type` == TopicTypes.FootballMatch)

      Future.traverse(footballMatchTopics) { topic =>
        isMatchEnded(topic.name).map {
          case true => Some(topic)
          case false => None
        }
      }.map(_.flatten)
    }

    def cachedMatchStatus(matchId: String): Future[String] = matchStatusCache(matchId) {
      client.matchInfo(matchId) map (_.matchStatus)
    }

    private def isMatchEnded(matchId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      cachedMatchStatus(matchId) map {
        case matchStatus if matchEndedStatuses contains matchStatus => true
        case _ => false
      } recover {
        case _ =>
          Logger.error(s"Unable to determine match status of $matchId.  Assuming that it is in the future")
          false
      }
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
