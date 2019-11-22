package notification.services

import models.Link.Internal
import models.{BreakingNewsNotification, GITContent, Link, Notification}

import scala.concurrent.Future

class ArticlePurge(fastlyPurge: FastlyPurge) {
  private def getContentApiId(notification: Notification): Option[String] = {
    def linkToContentApiId(link: Link): Option[String] = link match {
      case Internal(contentApiId, _, GITContent, _) => Some(contentApiId)
      case _ => None
    }

    notification match {
      case breaking: BreakingNewsNotification => linkToContentApiId(breaking.link)
      case _ => None
    }
  }

  def purgeFromNotification(notification: Notification): Future[Boolean] = {
    getContentApiId(notification) match {
      case Some(contentApiId) => fastlyPurge.softPurge(contentApiId)
      case _ => Future.successful(false)
    }
  }
}
