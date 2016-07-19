package notification.models.android

import java.net.URI
import java.util.UUID

import models.NotificationType.{BreakingNews, Content, GoalAlert}
import models._
import notification.models.android.Editions.Edition
import notification.services.azure.PlatformUriType
import play.api.libs.json._

sealed trait Notification {
  def uniqueIdentifier: UUID
  def `type`: String
  def title: String
  def message: String
  def thumbnailUrl: Option[URI]
  def topics: Set[Topic]
  def debug: Boolean
  def payload: Map[String, String]
}

case class BreakingNewsNotification(
  notificationType: NotificationType = BreakingNews,
  uniqueIdentifier: UUID,
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
    "uniqueIdentifier" -> uniqueIdentifier.toString,
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
  uniqueIdentifier: UUID,
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
    "uniqueIdentifier" -> uniqueIdentifier.toString,
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
  uniqueIdentifier: UUID,
  AWAY_TEAM_NAME: String,
  AWAY_TEAM_SCORE: Int,
  HOME_TEAM_NAME: String,
  HOME_TEAM_SCORE: Int,
  SCORING_TEAM_NAME: String,
  SCORER_NAME: String,
  GOAL_MINS: Int,
  OTHER_TEAM_NAME: String,
  matchId: String,
  mapiUrl: URI,
  uri: URI,
  uriType: PlatformUriType,
  debug: Boolean
) extends Notification {
  def topics: Set[Topic] = Set.empty
  def thumbnailUrl: Option[URI] = None
  def message: String = ""
  def title: String = ""
  def payload: Map[String, String] = Map(
    "type" -> `type`,
    "uniqueIdentifier" -> uniqueIdentifier.toString,
    "AWAY_TEAM_NAME" -> AWAY_TEAM_NAME,
    "AWAY_TEAM_SCORE" -> AWAY_TEAM_SCORE.toString,
    "HOME_TEAM_NAME" -> HOME_TEAM_NAME,
    "HOME_TEAM_SCORE" -> HOME_TEAM_SCORE.toString,
    "SCORING_TEAM_NAME" -> SCORING_TEAM_NAME,
    "SCORER_NAME" -> SCORER_NAME,
    "GOAL_MINS" -> GOAL_MINS.toString,
    "OTHER_TEAM_NAME" -> OTHER_TEAM_NAME,
    "matchId" -> matchId,
    "mapiUrl" -> mapiUrl.toString,
    "uri" -> uri.toString,
    "uriType" -> uriType.toString,
    "debug" -> debug.toString
  )
}