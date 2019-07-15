package registration.auditor

import models.{Topic, TopicTypes}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class LiveblogAuditor(wsClient: WSClient, config: AuditorApiConfig) extends Auditor {

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
