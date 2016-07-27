package notification.services.azure

import java.net.URI

import _root_.azure.{GCMBody, GCMRawPush, Tags}
import models._
import notification.models.Destination._
import notification.models.android.AndroidMessageTypes
import notification.models.android.Editions.Edition
import notification.models.{Push, android}
import notification.services.Configuration
import play.api.Logger

import scala.PartialFunction._
import PlatformUriTypes.{Item, FootballMatch, External}

sealed trait PlatformUriType

object PlatformUriTypes {

  case object Item extends PlatformUriType {
    override def toString: String = "item"
  }

  case object FootballMatch extends PlatformUriType {
    override def toString: String = "football-match"
  }

  case object External extends PlatformUriType {
    override def toString: String = "external"
  }

}

class GCMPushConverter(conf: Configuration) {

  val logger = Logger(classOf[GCMPushConverter])

  def toRawPush(push: Push): GCMRawPush = {
    logger.debug(s"Converting push to Azure: $push")
    GCMRawPush(
      body = GCMBody(data = toAzure(push.notification).payload),
      tags = toTags(push.destination)
    )
  }

  private[services] def toAzure(np: Notification, editions: Set[Edition] = Set.empty): android.Notification = np match {
    case ga: GoalAlertNotification => toGoalAlert(ga)
    case ca: ContentNotification => toContent(ca)
    case bn: BreakingNewsNotification => toBreakingNews(bn, editions)
  }

  private[services] def toTags(destination: Destination) = destination match {
    case Left(topics: Set[Topic]) => Some(Tags.fromTopics(topics))
    case Right(user: UserId) => Some(Tags.fromUserId(user))
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId")
    case Link.External(url) => new URI(url)
  }

  private def toBreakingNews(breakingNews: BreakingNewsNotification, editions: Set[Edition]) = {

    val sectionLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITSection) => contentApiId
    }

    val tagLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITTag) => contentApiId
    }

    val link = toPlatformLink(breakingNews.link)

    android.BreakingNewsNotification(
      `type` = AndroidMessageTypes.Custom,
      id = breakingNews.id,
      notificationType = breakingNews.`type`,
      title = breakingNews.title,
      ticker = breakingNews.message,
      message = breakingNews.message,
      debug = conf.debug,
      editions = editions,
      link = toAndroidLink(breakingNews.link),
      topics = breakingNews.topic,
      uriType = link.`type`,
      uri = link.uri,
      section = sectionLink.map(new URI(_)),
      edition = if (editions.size == 1) Some(editions.head) else None,
      keyword = tagLink.map(new URI(_)),
      imageUrl = breakingNews.imageUrl,
      thumbnailUrl = breakingNews.thumbnailUrl
    )
  }

  private def toContent(cn: ContentNotification) = {
    val link = toPlatformLink(cn.link)

    android.ContentNotification(
      id = cn.id,
      title = cn.title,
      ticker = cn.message,
      message = cn.message,
      link = toAndroidLink(cn.link),
      topics = cn.topic,
      uriType = link.`type`,
      uri = new URI(link.uri),
      thumbnailUrl = cn.thumbnailUrl,
      debug = conf.debug
    )
  }

  private def toGoalAlert(goalAlert: GoalAlertNotification) = android.GoalAlertNotification(
    `type` = AndroidMessageTypes.GoalAlert,
    id = goalAlert.id,
    awayTeamName = goalAlert.awayTeamName,
    awayTeamScore = goalAlert.awayTeamScore,
    homeTeamName = goalAlert.homeTeamName,
    homeTeamScore = goalAlert.homeTeamScore,
    scoringTeamName = goalAlert.scoringTeamName,
    scorerName = goalAlert.scorerName,
    goalMins = goalAlert.goalMins,
    otherTeamName = goalAlert.otherTeamName,
    matchId = goalAlert.matchId,
    mapiUrl = goalAlert.mapiUrl,
    debug = conf.debug,
    uri = new URI(replaceHost(goalAlert.mapiUrl)),
    uriType = FootballMatch
  )

  case class PlatformUri(uri: String, `type`: PlatformUriType)

  protected def replaceHost(uri: URI) = List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString

  protected def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"x-gu:///items/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  protected def mapWithOptionalValues(elems: (String, String)*)(optionals: (String, Option[String])*) =
    elems.toMap ++ optionals.collect { case (k, Some(v)) => k -> v }
}