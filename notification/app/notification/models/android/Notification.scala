package notification.models.android

import java.net.URI
import java.util.UUID

import models.NotificationType.BreakingNews
import models._
import notification.models.android.Editions.Edition
import notification.services.azure.PlatformUriType

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
    "notificationType" -> notificationType.toString,
    "uniqueIdentifier" -> id.toString,
    "type" -> `type`,
    "title" -> title,
    "ticker" -> ticker,
    "message" -> message,
    "debug" -> debug.toString,
    "editions" -> editions.mkString(","),
    "link" -> link.toString,
    "topics" -> topics.map(_.toString).mkString(","),
    "uriType" -> uriType.toString,
    "uri" -> uri) ++ (Map(
    "section" -> section.map(_.toString),
    "edition" -> edition.map(_.toString),
    "keyword" -> keyword.map(_.toString),
    "imageUrl" -> imageUrl.map(_.toString),
    "thumbnailUrl" -> thumbnailUrl.map(_.toString)
  ) collect {
    case (k, Some(v)) => k -> v
  })
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
    "type" -> `type`,
    "uniqueIdentifier" -> id.toString,
    "title" -> title,
    "ticker" -> ticker,
    "message" -> message,
    "link" -> link.toString,
    "topics" -> topics.map(_.toString).mkString(","),
    "uriType" -> uriType.toString,
    "uri" -> uri.toString,
    "debug" -> debug.toString
  ) ++ (Map("thumbnailUrl" -> thumbnailUrl.map(_.toString)) collect { case (k, Some(v)) => k -> v })
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
    "type" -> `type`,
    "uniqueIdentifier" -> id.toString,
    "AWAY_TEAM_NAME" -> awayTeamName,
    "AWAY_TEAM_SCORE" -> awayTeamScore.toString,
    "HOME_TEAM_NAME" -> homeTeamName,
    "HOME_TEAM_SCORE" -> homeTeamScore.toString,
    "SCORING_TEAM_NAME" -> scoringTeamName,
    "SCORER_NAME" -> scorerName,
    "GOAL_MINS" -> goalMins.toString,
    "OTHER_TEAM_NAME" -> otherTeamName,
    "matchId" -> matchId,
    "mapiUrl" -> mapiUrl.toString,
    "uri" -> uri.toString,
    "uriType" -> uriType.toString,
    "debug" -> debug.toString
  )
}