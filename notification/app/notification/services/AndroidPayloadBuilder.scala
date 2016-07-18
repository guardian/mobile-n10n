package notification.services

import models._
import notification.models.android.Editions.Edition
import notification.models.android.Keys

import scala.PartialFunction._

object AndroidPayloadBuilder extends PlatformPayloadBuilder {
  def build(np: Notification, editions: Set[Edition] = Set.empty): Map[String, String] = np match {
    case ga: GoalAlertNotification => buildGoalAlert(ga)
    case ca: ContentNotification => buildContentAlert(ca)
    case bn: BreakingNewsNotification => buildBreakingNews(bn, editions)
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _) => s"x-gu://www.guardian.co.uk/$contentApiId"
    case Link.External(url) => url
  }

  private def buildContentAlert(contentAlert: ContentNotification) = {
    val link = toPlatformLink(contentAlert.link)

    mapWithOptionalValues(
      Keys.Type -> Custom,
      Keys.uniqueIdentifier -> contentAlert.id.toString,
      Keys.Title -> contentAlert.title,
      Keys.Ticker -> contentAlert.message,
      Keys.Message -> contentAlert.message,
      Keys.Link -> toAndroidLink(contentAlert.link),
      Keys.Topics -> contentAlert.topic.map(_.toString).mkString(","),
      Keys.UriType -> link.`type`.toString,
      Keys.Uri -> link.uri
    )(
      Keys.ThumbnailUrl -> contentAlert.thumbnailUrl.map(_.toString)
    )
  }

  private def buildGoalAlert(goalAlert: GoalAlertNotification) = {
    Map(
      Keys.Type -> GoalAlert,
      Keys.uniqueIdentifier -> goalAlert.id,
      Keys.AwayTeamName -> goalAlert.awayTeamName,
      Keys.AwayTeamScore -> goalAlert.awayTeamScore.toString,
      Keys.HomeTeamName -> goalAlert.homeTeamName,
      Keys.HomeTeamScore -> goalAlert.homeTeamScore.toString,
      Keys.ScoringTeamName -> goalAlert.scoringTeamName,
      Keys.ScorerName -> goalAlert.scorerName,
      Keys.GoalMins -> goalAlert.goalMins.toString,
      Keys.OtherTeamName -> goalAlert.otherTeamName,
      Keys.MatchId -> goalAlert.matchId,
      Keys.MapiUrl -> goalAlert.mapiUrl.toString,
      //keys.Debug -> goalAlert.debug.toString,
      Keys.Uri -> replaceHost(goalAlert.mapiUrl),
      Keys.UriType -> FootballMatch.toString
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

    mapWithOptionalValues(
      Keys.Type -> Custom,
      Keys.uniqueIdentifier -> breakingNews.id.toString,
      Keys.NotificationType -> breakingNews.`type`.toString,
      Keys.Title -> breakingNews.title,
      Keys.Ticker -> breakingNews.message,
      Keys.Message -> breakingNews.message,
      //keys.Debug -> breakingNews.debug.toString,
      Keys.Editions -> editions.mkString(","),
      Keys.Link -> toAndroidLink(breakingNews.link),
      Keys.Topics -> breakingNews.topic.map(_.toString).mkString(","),
      Keys.UriType -> link.`type`.toString,
      Keys.Uri -> link.uri
    )(
      Keys.Section -> sectionLink,
      Keys.Edition -> (if (editions.size == 1) Some(editions.head.toString) else None),
      Keys.Keyword -> tagLink,
      Keys.ImageUrl -> breakingNews.imageUrl.map(_.toString),
      Keys.ThumbnailUrl -> breakingNews.thumbnailUrl.map(_.toString)
    )
  }
}