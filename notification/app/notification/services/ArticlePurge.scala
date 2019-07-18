package notification.services

import models.Link.Internal
import models.{BreakingNewsNotification, GITContent, Link, Notification}

import scala.concurrent.Future

class ArticlePurge(fastlyPurge: FastlyPurge, configuration: Configuration) {
  private def breakingNewsUrl(notification: Notification): Option[String] = {
    def linkToUrl(link: Link): Option[String] = link match {
      case Internal(contentApiId, _, GITContent) => Some(s"${configuration.mapiEndpointBase}/items/$contentApiId")
      case _ => None
    }

    notification match {
      case breaking: BreakingNewsNotification => linkToUrl(breaking.link)
      case _ => None
    }
  }

  def purgeFromNotification(notification: Notification): Future[Boolean] = {
    breakingNewsUrl(notification) match {
      case Some(url) => fastlyPurge.softPurge(url)
      case _ => Future.successful(false)
    }
  }
}
