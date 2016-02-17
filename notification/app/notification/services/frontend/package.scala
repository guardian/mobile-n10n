package notification.services

import java.net.URI
import java.util.UUID

import models.Link._
import models.{BreakingNewsNotification}
import org.joda.time.DateTime
import play.api.libs.json.Json

import scala.PartialFunction.condOpt

package object frontend {

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

    implicit val jf = Json.format[NewsAlert]

    def fromNotification(notification: BreakingNewsNotification, sent: DateTime): Option[NewsAlert] = {
      val urlId = condOpt(notification.link) { case Internal(contentId) => new URI(contentId) }
      urlId.map { capiIdUri =>
        NewsAlert(
          uid = notification.id,
          urlId = capiIdUri,
          title = notification.title,
          message = notification.message,
          link = new URI(s"http://www.theguardian.com/$urlId"),
          thumbnailUrl = notification.thumbnailUrl,
          imageUrl = notification.thumbnailUrl,
          publicationDate = sent,
          topics = notification.topic.map(_.toString)
        )
      }
    }
  }
}

