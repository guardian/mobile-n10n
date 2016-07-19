package notification.models.android

import java.net.URI
import java.util.UUID

import models.NotificationType.{BreakingNews, Content, GoalAlert}
import models._
import play.api.libs.json._

sealed trait Notification {
  def uniqueIdentifier: UUID
  def `type`: String
  def title: String
  def message: String
  def thumbnailUrl: Option[URI]
  def topics: String
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
  editions: String,
  link: URI,
  topics: String,
  uriType: String,
  uri: String,
  section: Option[URI],
  edition: Option[String],
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
    "editions" -> editions,
    "link" -> link.toString,
    "topics" -> topics,
    "uriType" -> uriType,
    "uri" -> uri) ++ (Map(
    "section" -> section.map(_.toString),
    "edition" -> edition,
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
  topics: String,
  uriType: String,
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
    "topics" -> topics,
    "uriType" -> uriType,
    "uri" -> uri.toString,
    "debug" -> debug.toString
  ) ++ (Map("thumbnailUrl" -> thumbnailUrl.map(_.toString)) collect { case (k, Some(v)) => k -> v })
}

case class GoalAlertNotification(
  `type`: String = AndroidMessageTypes.GoalAlert,
  uniqueIdentifier: UUID,
  AWAY_TEAM_NAME: String,
  AWAY_TEAM_SCORE: String,
  HOME_TEAM_NAME: String,
  HOME_TEAM_SCORE: String,
  SCORING_TEAM_NAME: String,
  SCORER_NAME: String,
  GOAL_MINS: String,
  OTHER_TEAM_NAME: String,
  matchId: String,
  mapiUrl: URI,
  uri: URI,
  uriType: String,
  debug: Boolean
) extends Notification {
  def topics: String = ""
  def thumbnailUrl: Option[URI] = None
  def message: String = ""
  def title: String = ""
  def payload: Map[String, String] = Map(
    "type" -> `type`,
    "uniqueIdentifier" -> uniqueIdentifier.toString,
    "AWAY_TEAM_NAME" -> AWAY_TEAM_NAME,
    "AWAY_TEAM_SCORE" -> AWAY_TEAM_SCORE,
    "HOME_TEAM_NAME" -> HOME_TEAM_NAME,
    "HOME_TEAM_SCORE" -> HOME_TEAM_SCORE,
    "SCORING_TEAM_NAME" -> SCORING_TEAM_NAME,
    "SCORER_NAME" -> SCORER_NAME,
    "GOAL_MINS" -> GOAL_MINS,
    "OTHER_TEAM_NAME" -> OTHER_TEAM_NAME,
    "matchId" -> matchId,
    "mapiUrl" -> mapiUrl.toString,
    "uri" -> uri.toString,
    "uriType" -> uriType,
    "debug" -> debug.toString
  )
}