package notification.services.frontend

import java.net.URI
import java.util.UUID
import models.BreakingNewsNotification
import models.Link.Internal
import org.joda.time.DateTime
import play.api.libs.json.{Json, OFormat}

import scala.PartialFunction.condOpt

case class NewsAlert(
  uid: UUID,
  urlId: URI,
  title: String,
  message: String,
  thumbnailUrl: Option[URI] = None,
  link: URI,
  imageUrl: Option[URI] = None,
  publicationDate: DateTime,
  topics: Set[String])

object NewsAlert {
  import models.JsonUtils._

  implicit val jf: OFormat[NewsAlert] = Json.format[NewsAlert]

  def fromNotification(notification: BreakingNewsNotification, sent: DateTime): Option[NewsAlert] = {
    val urlId = condOpt(notification.link) { case Internal(contentId, _, _, _) => new URI(contentId) }
    urlId.map { capiIdUri =>
      NewsAlert(
        uid = notification.id,
        urlId = capiIdUri,
        title = notification.title.getOrElse(""),
        message = notification.message.getOrElse(""),
        link = new URI(s"http://www.theguardian.com/$capiIdUri"),
        thumbnailUrl = notification.thumbnailUrl,
        imageUrl = notification.thumbnailUrl,
        publicationDate = sent,
        topics = notification.topic.map(_.toString).toSet
      )
    }
  }
}

