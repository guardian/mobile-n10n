package models

import java.util.UUID
import models.NotificationType._
import play.api.libs.json._
import java.net.URI
import models.elections.ElectionResults

sealed trait Notification {
  def id: UUID
  def `type`: NotificationType
  def sender: String
  def title: String
  def message: String
  def importance: Importance
  def topic: Set[Topic]
  def withTopics(topics: Set[Topic]): Notification
}

object Notification {

  implicit val jf = new Format[Notification] {
    override def writes(o: Notification): JsValue = o match {
      case n: BreakingNewsNotification => BreakingNewsNotification.jf.writes(n)
      case n: ContentNotification => ContentNotification.jf.writes(n)
      case n: GoalAlertNotification => GoalAlertNotification.jf.writes(n)
      case n: ElectionNotification => ElectionNotification.jf.writes(n)
      case n: LiveEventNotification => LiveEventNotification.jf.writes(n)
    }
    override def reads(json: JsValue): JsResult[Notification] = {
      json \ "type" match {
        case JsDefined(JsString("news")) => BreakingNewsNotification.jf.reads(json)
        case JsDefined(JsString("content")) => ContentNotification.jf.reads(json)
        case JsDefined(JsString("goal")) => GoalAlertNotification.jf.reads(json)
        case JsDefined(JsString("election")) => ElectionNotification.jf.reads(json)
        case JsDefined(JsString("live-notification")) => LiveEventNotification.jf.reads(json)
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
) extends Notification with NotificationWithLink {
  override def withTopics(topics: Set[Topic]): Notification = copy(topic = topics)
}

object BreakingNewsNotification {
  import JsonUtils._
  implicit val jf = Json.format[BreakingNewsNotification]
}

case class ContentNotification(
  id: UUID,
  `type`: NotificationType = Content,
  title: String,
  message: String,
  iosUseMessage: Option[Boolean],
  thumbnailUrl: Option[URI],
  sender: String,
  link: Link,
  importance: Importance,
  topic: Set[Topic]
) extends Notification with NotificationWithLink {
  override def withTopics(topics: Set[Topic]): Notification = copy(topic = topics)
}

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
) extends Notification {
  override def withTopics(topics: Set[Topic]): Notification = copy(topic = topics)
}

object GoalAlertNotification {
  import JsonUtils._
  implicit val jf = Json.format[GoalAlertNotification]
}

case class ElectionNotification(
  id: UUID,
  `type`: NotificationType = ElectionsAlert,
  sender: String,
  title: String,
  message: String,
  expandedMessage: Option[String],
  shortMessage: Option[String],
  importance: Importance,
  link: Link,
  resultsLink: Link,
  results: ElectionResults,
  topic: Set[Topic]
) extends Notification {
  override def withTopics(topics: Set[Topic]): Notification = copy(topic = topics)
}

object ElectionNotification {
  import JsonUtils._
  implicit val jf = Json.format[ElectionNotification]
}

case class LiveEventNotification(
  id: UUID,
  `type`: NotificationType = LiveEventAlert,
  sender: String,
  title: String,
  message: String,
  expandedMessage: Option[String],
  shortMessage: Option[String],
  importance: Importance,
  link1: Link,
  link2: Link,
  imageUrl: Option[URI],
  topic: Set[Topic]
) extends Notification {
  override def withTopics(topics: Set[Topic]): Notification = copy(topic = topics)
}

object LiveEventNotification {
  import JsonUtils._
  implicit val jf = Json.format[LiveEventNotification]
}
