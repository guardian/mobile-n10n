package notification.services.azure

import java.net.URI

import azure._
import models._
import notification.models.Destination._
import notification.models.android.Editions.Edition
import notification.models.{Push, ios}
import notification.services.Configuration
import play.api.Logger
import PlatformUriTypes.{External, FootballMatch, Item}
import models.Importance.Major

class APNSPushConverter(conf: Configuration) {

  val logger = Logger(classOf[APNSPushConverter])

  def toRawPush(push: Push): APNSRawPush = {
    logger.debug(s"Converting push to Azure: $push")
    APNSRawPush(
      body = toAzure(push.notification).payload,
      tags = toTags(push.destination)
    )
  }

  private def toBreakingNews(breakingNews: BreakingNewsNotification, editions: Set[Edition]) = {

    val link = toPlatformLink(breakingNews.link)

    ios.BreakingNewsNotification(
      category = "ITEM_CATEGORY",
      message = breakingNews.message,
      link = toIosLink(breakingNews.link), //check this
      topics = breakingNews.topic,
      uri = new URI(link.uri),
      uriType = link.`type`,
      legacyLink = toIosLink(breakingNews.link).toString, //check this
      imageUrl = breakingNews.thumbnailUrl orElse breakingNews.imageUrl
    )
  }

  private def toContent(cn: ContentNotification) = {
    val link = toPlatformLink(cn.link)

    ios.ContentNotification(
      category = "ITEM_CATEGORY",
      message = if (cn.iosUseMessage.contains(true)) cn.message else cn.title,
      link = toIosLink(cn.link),
      topics = cn.topic,
      uri = new URI(link.uri),
      uriType = link.`type`,
      legacyLink = toIosLink(cn.link).toString
    )
  }

  private def toGoalAlert(goalAlert: GoalAlertNotification) = {
    ios.GoalAlertNotification(
      message = goalAlert.message,
      id = goalAlert.id,
      uri = replaceHost(goalAlert.mapiUrl),
      uriType = FootballMatch,
      debug = false
    )
  }

  private def toElectionAlert(electionAlert: ElectionNotification) = {
    val democratVotes = electionAlert.results.candidates.find(_.name == "Clinton").map(_.electoralVotes).getOrElse(0)
    val republicanVotes = electionAlert.results.candidates.find(_.name == "Trump").map(_.electoralVotes).getOrElse(0)

    val textonlyFallback = s"• Electoral votes: Clinton $democratVotes, Trump $republicanVotes\n• 270 electoral votes to win\n"
    ios.ElectionNotification(
      message = electionAlert.message,
      id = electionAlert.id,
      title = electionAlert.title,
      body = electionAlert.message,
      richBody = electionAlert.expandedMessage.getOrElse(electionAlert.message),
      democratVotes = democratVotes,
      republicanVotes = republicanVotes,
      link = toIosLink(electionAlert.link),
      resultsLink = toIosLink(electionAlert.resultsLink),
      buzz = electionAlert.importance == Major
    )
  }

  case class PlatformUri(uri: String, `type`: PlatformUriType)

  private def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"x-gu:///items/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  private def toAzure(np: Notification, editions: Set[Edition] = Set.empty): ios.Notification = np match {
    case ga: GoalAlertNotification => toGoalAlert(ga)
    case ca: ContentNotification => toContent(ca)
    case bn: BreakingNewsNotification => toBreakingNews(bn, editions)
    case el: ElectionNotification => toElectionAlert(el)
  }

  private def toTags(destination: Destination) = destination match {
    case Left(topics: Set[Topic]) => Some(Tags.fromTopics(topics))
    case Right(user: UniqueDeviceIdentifier) => Some(Tags.fromUserId(user))
  }

  private def toIosLink(link: Link) = link match {
    case Link.Internal(contentApiId, Some(shortUrl), _) => new URI(s"x-gu://${new URI(shortUrl).getPath}")
    case _ => link.webUri(conf.frontendBaseUrl)
  }

  private def replaceHost(uri: URI) =
    new URI(List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString)

}