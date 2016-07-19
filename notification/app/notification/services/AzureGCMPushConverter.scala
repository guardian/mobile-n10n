package notification.services

import models._
import notification.models.android.Editions.Edition
import notification.models.android.AndroidMessageTypes
import notification.models.{Push, android}

import scala.PartialFunction._
import java.net.URI

import azure.{GCMBody, GCMRawPush, Tags}
import notification.models.Destination.{apply => _, _}
import play.api.Logger

class AzureGCMPushConverter(conf: Configuration) {

  val logger = Logger(classOf[AzureGCMPushConverter])

  def toRawPush(push: Push): GCMRawPush = {
    logger.debug(s"Converting push to Azure: $push")
    GCMRawPush(
      body = GCMBody(data = toAzure(push.notification).payload),
      tags = toTags(push.destination)
    )
  }

  private def toAzure(np: Notification, editions: Set[Edition] = Set.empty): android.Notification = np match {
    case ga: GoalAlertNotification => buildGoalAlert(ga)
    case ca: ContentNotification => buildContentAlert(ca)
    case bn: BreakingNewsNotification => buildBreakingNews(bn, editions)
  }

  private[services] def toTags(destination: Destination) = destination match {
    case Left(topics: Set[Topic]) => Some(Tags.fromTopics(topics))
    case Right(user: UserId) => Some(Tags.fromUserId(user))
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId")
    case Link.External(url) => new URI(url)
  }

  private def buildContentAlert(contentAlert: ContentNotification) = {
    val link = toPlatformLink(contentAlert.link)

    android.ContentNotification(
      uniqueIdentifier = contentAlert.id,
      title = contentAlert.title,
      ticker = contentAlert.message,
      message = contentAlert.message,
      link = toAndroidLink(contentAlert.link),
      topics = contentAlert.topic.map(_.toString).mkString(","),
      uriType = link.`type`.toString,
      uri = new URI(link.uri),
      thumbnailUrl = contentAlert.thumbnailUrl,
      debug = conf.debug
    )
  }

  private def buildGoalAlert(goalAlert: GoalAlertNotification) = {
    android.GoalAlertNotification(
      `type` = AndroidMessageTypes.GoalAlert,
      uniqueIdentifier = goalAlert.id,
      AWAY_TEAM_NAME = goalAlert.awayTeamName,
      AWAY_TEAM_SCORE = goalAlert.awayTeamScore.toString,
      HOME_TEAM_NAME = goalAlert.homeTeamName,
      HOME_TEAM_SCORE = goalAlert.homeTeamScore.toString,
      SCORING_TEAM_NAME = goalAlert.scoringTeamName,
      SCORER_NAME = goalAlert.scorerName,
      GOAL_MINS = goalAlert.goalMins.toString,
      OTHER_TEAM_NAME = goalAlert.otherTeamName,
      matchId = goalAlert.matchId,
      mapiUrl = goalAlert.mapiUrl,
      debug = conf.debug,
      uri = new URI(replaceHost(goalAlert.mapiUrl)),
      uriType = FootballMatch.toString
    )
  }

  private def buildBreakingNews(breakingNews: BreakingNewsNotification, editions: Set[Edition]) = {

    val sectionLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, GITSection) => contentApiId
    }

    val tagLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, GITTag) => contentApiId
    }

    val link = toPlatformLink(breakingNews.link)

    android.BreakingNewsNotification(
      `type` = AndroidMessageTypes.Custom,
      uniqueIdentifier = breakingNews.id,
      notificationType = breakingNews.`type`,
      title = breakingNews.title,
      ticker = breakingNews.message,
      message = breakingNews.message,
      debug = conf.debug,
      editions = editions.mkString(","),
      link = toAndroidLink(breakingNews.link),
      topics = breakingNews.topic.map(_.toString).mkString(","),
      uriType = link.`type`.toString,
      uri = link.uri,
      section = sectionLink.map(new URI(_)),
      edition = if (editions.size == 1) Some(editions.head.toString) else None,
      keyword = tagLink.map(new URI(_)),
      imageUrl = breakingNews.imageUrl,
      thumbnailUrl = breakingNews.thumbnailUrl
    )
  }

  sealed trait PlatformUriType

  case object Item extends PlatformUriType {
    override def toString: String = "item"
  }

  case object FootballMatch extends PlatformUriType {
    override def toString: String = "football-match"
  }

  case object External extends PlatformUriType {
    override def toString: String = "external"
  }

  case class PlatformUri(uri: String, `type`: PlatformUriType)

  protected def replaceHost(uri: URI) = List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString

  protected def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId, _) => PlatformUri(s"x-gu:///items/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  protected def mapWithOptionalValues(elems: (String, String)*)(optionals: (String, Option[String])*) =
    elems.toMap ++ optionals.collect { case (k, Some(v)) => k -> v }
}