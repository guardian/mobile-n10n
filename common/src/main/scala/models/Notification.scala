package models

import java.net.URI
import java.util.UUID

import ai.x.play.json.Encoders.encoder
import ai.x.play.json.Jsonx
import models.NotificationType._
import play.api.libs.json._

sealed trait Notification {
  def id: UUID
  def `type`: NotificationType
  def sender: String
  def title: Option[String]
  def message: Option[String]
  def importance: Importance
  def topic: List[Topic]
  def dryRun: Option[Boolean]
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
      case n: EditionsNotification => EditionsNotification.jf.writes(n)
      case n: Us2020ResultsNotification => Us2020ResultsNotification.jf.writes(n)
    }
    override def reads(json: JsValue): JsResult[Notification] = {
      (json \ "type").validate[NotificationType].flatMap {
        case BreakingNews => BreakingNewsNotification.jf.reads(json)
        case Content => ContentNotification.jf.reads(json)
        case GoalAlert => GoalAlertNotification.jf.reads(json)
        case LiveEventAlert => LiveEventNotification.jf.reads(json)
        case FootballMatchStatus => FootballMatchStatusNotification.jf.reads(json)
        case NewsstandShard => NewsstandShardNotification.jf.reads(json)
        case Editions => EditionsNotification.jf.reads(json)
        case Us2020Results => Us2020ResultsNotification.jf.reads(json)
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
  title: Option[String],
  message: Option[String],
  thumbnailUrl: Option[URI],
  sender: String,
  link: Link,
  imageUrl: Option[URI],
  importance: Importance,
  topic: List[Topic],
  dryRun: Option[Boolean]
) extends Notification

object BreakingNewsNotification {
  implicit val jf = Json.format[BreakingNewsNotification]
}

case class NewsstandShardNotification(
  id: UUID,
  shard:Int,
  `type`: NotificationType = NewsstandShard
) extends Notification {
  override def title = None
  override def message: Option[String] = None
  override def sender: String = "newsstand-shard"
  override def importance: Importance = Importance.Minor
  override def topic: List[Topic] = List(Topic(TopicTypes.NewsstandShard, s"newsstand-shard-$shard"))
  override def dryRun = None

}
object NewsstandShardNotification {
  implicit val jf = Json.format[NewsstandShardNotification]
}

case class EditionsNotification(
  id: UUID,
  `type`: NotificationType = Editions,
  topic: List[Topic],
  key: String,
  name: String,
  date: String,
  sender: String,
  dryRun: Option[Boolean] = None
) extends Notification {
  override def title: Option[String] = None
  override def message: Option[String] = None
  override def importance: Importance = Importance.Minor
}

object EditionsNotification {
  implicit val jf = Json.format[EditionsNotification]
}

case class ContentNotification(
  id: UUID,
  `type`: NotificationType = Content,
  title: Option[String],
  message: Option[String],
  iosUseMessage: Option[Boolean],
  thumbnailUrl: Option[URI],
  sender: String,
  link: Link,
  importance: Importance,
  topic: List[Topic],
  dryRun: Option[Boolean]
) extends Notification with NotificationWithLink

object ContentNotification {
  implicit val jf = Json.format[ContentNotification]
}


case class FootballMatchStatusNotification(
  id: UUID,
  `type`: NotificationType = FootballMatchStatus,
  title: Option[String],
  message: Option[String],
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
  debug: Boolean,
  dryRun: Option[Boolean]
) extends Notification

object FootballMatchStatusNotification {

  implicit val jf: Format[FootballMatchStatusNotification] = Jsonx.formatCaseClass[FootballMatchStatusNotification]
}

case class GoalAlertNotification(
  id: UUID,
  `type`: NotificationType = GoalAlert,
  title: Option[String],
  message: Option[String],
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
  addedTime: Option[String],
  dryRun: Option[Boolean]
) extends Notification

object GoalAlertNotification {
  implicit val jf = Json.format[GoalAlertNotification]
}


case class LiveEventNotification(
  id: UUID,
  `type`: NotificationType = LiveEventAlert,
  sender: String,
  title: Option[String],
  message: Option[String],
  expandedMessage: Option[String],
  shortMessage: Option[String],
  importance: Importance,
  link1: Link,
  link2: Link,
  imageUrl: Option[URI],
  topic: List[Topic],
  dryRun: Option[Boolean]
) extends Notification

object LiveEventNotification {
  implicit val jf = Json.format[LiveEventNotification]
}

case class Us2020ResultsNotification (
  id: UUID,
  `type`: NotificationType = Content,
  sender: String,
  title: Option[String],
  link: Link,
  expandedTitle: String,
  leftCandidateName: String,
  leftCandidateColour: String,
  leftCandidateColourDark: String,
  leftCandidateDelegates: Int,
  leftCandidateVoteShare: String,
  rightCandidateName: String,
  rightCandidateColour: String,
  rightCandidateColourDark: String,
  rightCandidateDelegates: Int,
  rightCandidateVoteShare: String,
  totalDelegates: Int,
  delegatesToWin: String,
  message: Option[String],
  expandedMessage: String,
  button1Text: String,
  button1Url: String,
  button2Text: String,
  button2Url: String,
  stopButtonText: String,
  importance: Importance,
  topic: List[Topic],
  dryRun: Option[Boolean]
) extends Notification

object Us2020ResultsNotification {
  implicit val jf: Format[Us2020ResultsNotification] = Jsonx.formatCaseClass[Us2020ResultsNotification]
}
