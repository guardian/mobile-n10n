package registration.auditor

import models.{Topic, TopicTypes}
import pa.PaClient
import play.api.Logger
import utils.LruCache

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

case class FootballMatchAuditor(client: PaClient)(implicit ec: ExecutionContext) extends Auditor {

  private val matchStatusCache: LruCache[String] = LruCache[String](timeToLive = 5.minutes)

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
