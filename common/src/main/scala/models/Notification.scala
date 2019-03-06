package models

import java.util.UUID

import models.NotificationType._
import play.api.libs.json._
import java.net.URI

import ai.x.play.json.Jsonx

sealed trait Notification {
  def id: UUID
  def `type`: NotificationType
  def sender: String
  def title: String
  def message: String
  def importance: Importance
  def topic: List[Topic]
  def withTopics(topics: List[Topic]): Notification
}

object Notification {

  implicit val jf = new Format[Notification] {
    override def writes(o: Notification): JsValue = o match {
      case n: BreakingNewsNotification => BreakingNewsNotification.jf.writes(n)
      case n: ContentNotification => ContentNotification.jf.writes(n)
      case n: GoalAlertNotification => GoalAlertNotification.jf.writes(n)
      case n: LiveEventNotification => LiveEventNotification.jf.writes(n)
      case n: FootballMatchStatusNotification => FootballMatchStatusNotification.jf.writes(n)
      case n: NewsstandShardNotification => NewsstandShardNotification.jf.writes(n)
    }
    override def reads(json: JsValue): JsResult[Notification] = {
      (json \ "type").validate[NotificationType].flatMap {
        case BreakingNews => BreakingNewsNotification.jf.reads(json)
        case Content => ContentNotification.jf.reads(json)
        case GoalAlert => GoalAlertNotification.jf.reads(json)
        case LiveEventAlert => LiveEventNotification.jf.reads(json)
        case FootballMatchStatus => FootballMatchStatusNotification.jf.reads(json)
        case NewsstandShard => NewsstandShardNotification.jf.reads(json)
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
  topic: List[Topic]
) extends Notification with NotificationWithLink {
  override def withTopics(topics: List[Topic]): Notification = copy(topic = topics)
}

object BreakingNewsNotification {
  import JsonUtils._
  implicit val jf = Json.format[BreakingNewsNotification]
}

case class NewsstandShardNotification(
                                       id: UUID,
                                       shard:Int,
                                       `type`: NotificationType = NewsstandShard,
                                     ) extends Notification {
  override def title = ""
  override def message: String = ""
  override def sender: String = "newsstand-shard"
  override def importance: Importance = Importance.Minor
  override def withTopics(topics: List[Topic]): Notification = this
  override def topic: List[Topic] = List(Topic(TopicTypes.NewsstandShard, s"newsstand-shard-$shard"))

}
object NewsstandShardNotification {
  implicit val jf = Json.format[NewsstandShardNotification]
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
  topic: List[Topic]
) extends Notification with NotificationWithLink {
  override def withTopics(topics: List[Topic]): Notification = copy(topic = topics)
}

object ContentNotification {
  import JsonUtils._
  implicit val jf = Json.format[ContentNotification]
}


case class FootballMatchStatusNotification(
  id: UUID,
  `type`: NotificationType = FootballMatchStatus,
  title: String,
  message: String,
  thumbnailUrl: Option[URI] = None,
  sender: String,
  awayTeamName: String,
  awayTeamScore: Int,
  awayTeamMessage: String,
  awayTeamId: String,
  homeTeamName: String,
  homeTeamScore: Int,
  homeTeamMessage: String,
  homeTeamId: String,
  competitionName: Option[String],
  venue: Option[String],
  matchId: String,
  matchInfoUri: URI,
  articleUri: Option[URI],
  importance: Importance,
  topic: List[Topic],
  matchStatus: String,
  eventId: String,
  debug: Boolean
) extends Notification {
  override def withTopics(topics: List[Topic]): Notification = copy(topic = topics)
}

object FootballMatchStatusNotification {
  import JsonUtils._

  implicit val jf: Format[FootballMatchStatusNotification] = Jsonx.formatCaseClass[FootballMatchStatusNotification]
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
  topic: List[Topic],
  addedTime: Option[String]
) extends Notification {
  override def withTopics(topics: List[Topic]): Notification = copy(topic = topics)
}

object GoalAlertNotification {
  import JsonUtils._
  implicit val jf = Json.format[GoalAlertNotification]
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
  topic: List[Topic]
) extends Notification {
  override def withTopics(topics: List[Topic]): Notification = copy(topic = topics)
}

object LiveEventNotification {
  import JsonUtils._
  implicit val jf = Json.format[LiveEventNotification]
}
