package notification.services.azure

import java.net.URI

import _root_.azure.{GCMBody, GCMRawPush, Tags}
import models._
import notification.models.Destination._
import notification.models.android.AndroidMessageTypes
import notification.models.android.Editions.{Edition, UK}
import notification.models.{Push, android}
import notification.services.Configuration
import play.api.Logger

import scala.PartialFunction._
import PlatformUriTypes.{External, FootballMatch, Item}
import models.Importance.Major

class GCMPushConverter(conf: Configuration) {

  val logger = Logger(classOf[GCMPushConverter])

  def toRawPush(push: Push): GCMRawPush = {
    logger.debug(s"Converting push to Azure: $push")
    GCMRawPush(
      body = GCMBody(data = toAzure(push.notification).payload),
      tags = toTags(push.destination)
    )
  }

  private[services] def toAzure(np: Notification): android.Notification = np match {
    case ga: GoalAlertNotification => toGoalAlert(ga)
    case ca: ContentNotification => toContent(ca)
    case bn: BreakingNewsNotification => toBreakingNews(bn)
    case el: ElectionNotification => toElectionAlert(el)
  }

  private[services] def toTags(destination: Destination) = destination match {
    case Left(topics: Set[Topic]) => Some(Tags.fromTopics(topics))
    case Right(user: UniqueDeviceIdentifier) => Some(Tags.fromUserId(user))
  }

  private def toAndroidTopic(topic: Topic) = s"${topic.`type`}//${topic.name}"

  private def toBreakingNews(breakingNews: BreakingNewsNotification) = {

    val sectionLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITSection) => contentApiId
    }

    val tagLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITTag) => contentApiId
    }

    val link = toPlatformLink(breakingNews.link)

    val editions = breakingNews.topic
      .filter(_.`type` == TopicTypes.Breaking)
      .map(_.name)
      .collect(Edition.fromString)
    
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
      topics = breakingNews.topic.map(toAndroidTopic),
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
      topics = cn.topic.map(toAndroidTopic),
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

  private def toElectionAlert(electionAlert: ElectionNotification) = android.ElectionNotification(
    `type` = AndroidMessageTypes.ElectionAlert,
    id = electionAlert.id,
    title = electionAlert.title,
    expandedMessage = electionAlert.expandedMessage.getOrElse(electionAlert.message),
    shortMessage = electionAlert.shortMessage.getOrElse(electionAlert.message),
    results = electionAlert.results,
    link = toAndroidLink(electionAlert.link),
    resultsLink = toAndroidLink(electionAlert.resultsLink),
    importance = electionAlert.importance,
    debug = conf.debug
  )

  protected def replaceHost(uri: URI) = List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString

  protected def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"x-gu:///items/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId")
    case Link.External(url) => new URI(url)
  }
}