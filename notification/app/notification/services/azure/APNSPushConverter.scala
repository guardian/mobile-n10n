package notification.services.azure

import java.net.URI

import azure._
import models._
import notification.models.android.Editions.Edition
import notification.models.{Push, ios}
import notification.services.Configuration
import play.api.Logger
import PlatformUriTypes.{External, Item}
import azure.apns.{FootballMatchStatusProperties, LiveEventProperties}
import models.Importance.Major

import PartialFunction.condOpt

class APNSPushConverter(conf: Configuration) extends PushConverter {

  val logger = Logger(classOf[APNSPushConverter])

  def toRawPush(push: Push): Option[APNSRawPush] = {
    logger.debug(s"Converting push to Azure: $push")
    toAzure(push.notification) map { notification =>
      APNSRawPush(
        body = notification.payload,
        tags = toTags(push.destination)
      )
    }
  }

  private def toBreakingNews(breakingNews: BreakingNewsNotification, editions: Set[Edition]) = {

    val link = toPlatformLink(breakingNews.link)

    ios.BreakingNewsNotification(
      category = breakingNews.link match {
        case _: Link.External => ""
        case _: Link.Internal => "ITEM_CATEGORY"
      },
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

  private def toLiveEventAlert(liveEvent: LiveEventNotification) = {
    ios.LiveEventNotification(LiveEventProperties(
        title = liveEvent.title,
        body = liveEvent.message,
        richviewbody = liveEvent.expandedMessage.getOrElse(liveEvent.message),
        sound = if (liveEvent.importance == Major) 1 else 0,
        link1 = toIosLink(liveEvent.link1).toString,
        link2 = toIosLink(liveEvent.link2).toString,
        imageURL = liveEvent.imageUrl.map(_.toString),
        topics = liveEvent.topic.toList.map(_.toString).mkString(",")
      )
    )
  }

  private def toMatchStatusAlert(matchStatus: FootballMatchStatusNotification) = {
    ios.FootballMatchStatusNotification(
      title = matchStatus.title,
      body = matchStatus.message,
      matchStatus = FootballMatchStatusProperties(
        homeTeamName = matchStatus.homeTeamName,
        homeTeamId = matchStatus.homeTeamId,
        homeTeamScore = matchStatus.homeTeamScore,
        homeTeamText = matchStatus.homeTeamMessage,
        awayTeamName = matchStatus.awayTeamName,
        awayTeamId = matchStatus.awayTeamId,
        awayTeamScore = matchStatus.awayTeamScore,
        awayTeamText = matchStatus.awayTeamMessage,
        currentMinute = "",
        matchStatus = matchStatus.matchStatus,
        matchId = matchStatus.matchId,
        mapiUrl = matchStatus.matchInfoUri.toString,
        matchInfoUri = matchStatus.matchInfoUri.toString,
        articleUri = matchStatus.articleUri.map(_.toString),
        uri = "",
        competitionName = matchStatus.competitionName,
        venue = matchStatus.venue
      ),
      sound = matchStatus.importance == Importance.Major
    )
  }

  case class PlatformUri(uri: String, `type`: PlatformUriType)

  private def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"https://www.theguardian.com/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  def toNewsstandShardAlert(ns: NewsstandShardNotification) = ios.NewsstandNotificationShard(
    ns.id,
    ns.shard
  )

  private def toAzure(np: Notification, editions: Set[Edition] = Set.empty): Option[ios.Notification] = condOpt(np) {
    case ca: ContentNotification => toContent(ca)
    case bn: BreakingNewsNotification => toBreakingNews(bn, editions)
    case el: ElectionNotification => toElectionAlert(el)
    case mi: LiveEventNotification => toLiveEventAlert(mi)
    case fa: FootballMatchStatusNotification => toMatchStatusAlert(fa)
    case ns: NewsstandShardNotification => toNewsstandShardAlert(ns)
  }

  private def toTags(destination: Set[Topic]) = Some(Tags.fromTopics(destination))

  private def toIosLink(link: Link) = link match {
    case Link.Internal(contentApiId, Some(shortUrl), _) => new URI(s"x-gu://${new URI(shortUrl).getPath}")
    case _ => link.webUri(conf.frontendBaseUrl)
  }

  private def replaceHost(uri: URI) =
    new URI(List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString)

}