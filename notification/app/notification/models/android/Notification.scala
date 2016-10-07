package notification.models.android

import java.net.URI
import java.util.UUID

import models.NotificationType.BreakingNews
import models._
import notification.models.android.Editions.Edition
import notification.services.azure.PlatformUriType
import utils.MapImplicits._

sealed trait Notification {
  def payload: Map[String, String]
}

case class BreakingNewsNotification(
  notificationType: NotificationType = BreakingNews,
  id: UUID,
  `type`: String = AndroidMessageTypes.Custom,
  title: String,
  ticker: String,
  message: String,
  debug: Boolean,
  editions: Set[Edition],
  link: URI,
  topics: Set[Topic],
  uriType: PlatformUriType,
  uri: String,
  section: Option[URI],
  edition: Option[Edition],
  keyword: Option[URI],
  imageUrl: Option[URI],
  thumbnailUrl: Option[URI]
) extends Notification {
  def payload: Map[String, String] = Map(
    Keys.NotificationType -> notificationType.value,
    Keys.UniqueIdentifier -> id.toString,
    Keys.Type -> `type`,
    Keys.Title -> title,
    Keys.Ticker -> ticker,
    Keys.Message -> message,
    Keys.Debug -> debug.toString,
    Keys.Editions -> editions.mkString(","),
    Keys.Link -> link.toString,
    Keys.Topics -> topics.map(_.toString).mkString(","),
    Keys.UriType -> uriType.toString,
    Keys.Uri -> uri) ++ Map(
      Keys.Section -> section.map(_.toString),
      Keys.Edition -> edition.map(_.toString),
      Keys.Keyword -> keyword.map(_.toString),
      Keys.ImageUrl -> imageUrl.map(_.toString),
      Keys.ThumbnailUrl -> thumbnailUrl.map(_.toString)
    ).flattenValues
}

case class ContentNotification(
  `type`: String = AndroidMessageTypes.Custom,
  id: UUID,
  title: String,
  ticker: String,
  message: String,
  link: URI,
  topics: Set[Topic],
  uriType: PlatformUriType,
  uri: URI,
  thumbnailUrl: Option[URI],
  debug: Boolean
) extends Notification {
  def payload: Map[String, String] = Map(
    Keys.Type -> `type`,
    Keys.UniqueIdentifier -> id.toString,
    Keys.Title -> title,
    Keys.Ticker -> ticker,
    Keys.Message -> message,
    Keys.Link -> link.toString,
    Keys.Topics -> topics.map(_.toString).mkString(","),
    Keys.UriType -> uriType.toString,
    Keys.Uri -> uri.toString,
    Keys.Debug -> debug.toString
  ) ++ Map(Keys.ThumbnailUrl -> thumbnailUrl.map(_.toString)).flattenValues
}

case class GoalAlertNotification(
  `type`: String = AndroidMessageTypes.GoalAlert,
  id: UUID,
  awayTeamName: String,
  awayTeamScore: Int,
  homeTeamName: String,
  homeTeamScore: Int,
  scoringTeamName: String,
  scorerName: String,
  goalMins: Int,
  otherTeamName: String,
  matchId: String,
  mapiUrl: URI,
  uri: URI,
  uriType: PlatformUriType,
  debug: Boolean
) extends Notification {
  def payload: Map[String, String] = Map(
    Keys.Type -> `type`,
    Keys.UniqueIdentifier -> id.toString,
    Keys.AwayTeamName -> awayTeamName,
    Keys.AwayTeamScore -> awayTeamScore.toString,
    Keys.HomeTeamName -> homeTeamName,
    Keys.HomeTeamScore -> homeTeamScore.toString,
    Keys.ScoringTeamName -> scoringTeamName,
    Keys.ScorerName -> scorerName,
    Keys.GoalMins -> goalMins.toString,
    Keys.OtherTeamName -> otherTeamName,
    Keys.MatchId -> matchId,
    Keys.MapiUrl -> mapiUrl.toString,
    Keys.Uri -> uri.toString,
    Keys.UriType -> uriType.toString,
    Keys.Debug -> debug.toString
  )
}

case class ElectionNotification(
  `type`: String = AndroidMessageTypes.ElectionAlert,
  id: UUID,
  message: String,
  debug: Boolean
) extends Notification {
  def payload: Map[String, String] = Map(
    Keys.Type -> `type`,
    Keys.UniqueIdentifier -> id.toString,
    Keys.Message -> message,
    Keys.Debug -> debug.toString
  )
}