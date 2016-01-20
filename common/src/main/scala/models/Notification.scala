package models

import java.util.UUID
import models.NotificationType._
import play.api.libs.json._
import java.net.URI

sealed trait Notification {
  def id: UUID
  def `type`: NotificationType
  def sender: String
  def title: String
  def message: String
  def thumbnailUrl: Option[URI]
  def importance: Importance
  def topic: Set[Topic]
}

object Notification {

  implicit val jf = new Format[Notification] {
    override def writes(o: Notification): JsValue = o match {
      case n: BreakingNewsNotification => BreakingNewsNotification.jf.writes(n)
      case n: ContentNotification => ContentNotification.jf.writes(n)
      case n: GoalAlertNotification => GoalAlertNotification.jf.writes(n)
    }
    override def reads(json: JsValue): JsResult[Notification] = {
      json \ "type" match {
        case JsDefined(JsString("news")) => BreakingNewsNotification.jf.reads(json)
        case JsDefined(JsString("content")) => ContentNotification.jf.reads(json)
        case JsDefined(JsString("goalAlert")) => GoalAlertNotification.jf.reads(json)
        case _ => JsError("Unknown notification type")
      }
    }
  }
}

trait NotificationWithLink {
  self: Notification =>
  def link: Link
}

case class BreakingNewsNotification(
  id: UUID,
  `type`: NotificationType = BreakingNews,
  title: String,
  message: String,
  thumbnailUrl: Option[URI],
  sender: String,
  link: Link,
  imageUrl: Option[URI],
  importance: Importance,
  topic: Set[Topic]
) extends Notification with NotificationWithLink

object BreakingNewsNotification {
  import JsonUtils._
  implicit val jf = Json.format[BreakingNewsNotification]
}

case class ContentNotification(
  id: UUID,
  `type`: NotificationType = Content,
  title: String,
  message: String,
  thumbnailUrl: Option[URI],
  sender: String,
  link: Link,
  importance: Importance,
  topic: Set[Topic]
) extends Notification with NotificationWithLink

object ContentNotification {
  import JsonUtils._
  implicit val jf = Json.format[ContentNotification]
}

case class GoalAlertNotification(
  id: UUID,
  `type`: NotificationType = GoalAlert,
  title: String,
  message: String,
  thumbnailUrl: Option[URI] = None,
  sender: String,
  goalType: GoalType,
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
  importance: Importance,
  topic: Set[Topic],
  addedTime: Option[String]
) extends Notification

object GoalAlertNotification {
  import JsonUtils._
  implicit val jf = Json.format[GoalAlertNotification]
}