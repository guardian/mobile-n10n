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
      case n: FootballMatchStatusNotification => FootballMatchStatusNotification.jf.writes(n)
    }
    override def reads(json: JsValue): JsResult[Notification] = {
      (json \ "type").validate[NotificationType].flatMap {
        case BreakingNews => BreakingNewsNotification.jf.reads(json)
        case Content => ContentNotification.jf.reads(json)
        case GoalAlert => GoalAlertNotification.jf.reads(json)
        case ElectionsAlert => ElectionNotification.jf.reads(json)
        case LiveEventAlert => LiveEventNotification.jf.reads(json)
        case FootballMatchStatus => FootballMatchStatusNotification.jf.reads(json)
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
  mapiUrl: URI,
  importance: Importance,
  topic: Set[Topic],
  phase: String,
  eventId: String,
  debug: Boolean
) extends Notification {
  override def withTopics(topics: Set[Topic]): Notification = copy(topic = topics)
}

object FootballMatchStatusNotification {
  import JsonUtils._

  implicit val jf = new Format[FootballMatchStatusNotification] {
    override def reads(json: JsValue): JsResult[FootballMatchStatusNotification] = {
      for {
        id <- (json \ "id").validate[UUID]
        typ <- (json \ "type").validate[NotificationType]
        title <- (json \ "title").validate[String]
        message <- (json \ "message").validate[String]
        thumbnailUrl <- (json \ "thumbnailUrl").validateOpt[URI]
        sender <- (json \ "sender").validate[String]
        awayTeamName <- (json \ "awayTeamName").validate[String]
        awayTeamScore <- (json \ "awayTeamScore").validate[Int]
        awayTeamMessage <- (json \ "awayTeamMessage").validate[String]
        awayTeamId <- (json \ "awayTeamId").validate[String]
        homeTeamName <- (json \ "homeTeamName").validate[String]
        homeTeamScore <- (json \ "homeTeamScore").validate[Int]
        homeTeamMessage <- (json \ "homeTeamMessage").validate[String]
        homeTeamId <- (json \ "homeTeamId").validate[String]
        competitionName <- (json \ "competitionName").validateOpt[String]
        venue <- (json \ "venue").validateOpt[String]
        matchId <- (json \ "matchId").validate[String]
        mapiUrl <- (json \ "mapiUrl").validate[URI]
        importance <- (json \ "importance").validate[Importance]
        topic <- (json \ "topic").validate[Set[Topic]]
        phase <- (json \ "phase").validate[String]
        eventId <- (json \ "eventId").validate[String]
        debug <- (json \ "debug").validate[Boolean]
      } yield FootballMatchStatusNotification(
        id,
        typ,
        title,
        message,
        thumbnailUrl,
        sender,
        awayTeamName,
        awayTeamScore,
        awayTeamMessage,
        awayTeamId,
        homeTeamName,
        homeTeamScore,
        homeTeamMessage,
        homeTeamId,
        competitionName,
        venue,
        matchId,
        mapiUrl,
        importance,
        topic,
        phase,
        eventId,
        debug
      )
    }

    override def writes(o: FootballMatchStatusNotification): JsValue = JsObject(
      Seq(
        "id" -> Json.toJson(o.id),
        "type" -> Json.toJson(o.`type`),
        "title" -> Json.toJson(o.title),
        "message" -> Json.toJson(o.message),
        "thumbnailUrl" -> Json.toJson(o.thumbnailUrl),
        "sender" -> Json.toJson(o.sender),
        "awayTeamName" -> Json.toJson(o.awayTeamName),
        "awayTeamScore" -> Json.toJson(o.awayTeamScore),
        "awayTeamMessage" -> Json.toJson(o.awayTeamMessage),
        "awayTeamId" -> Json.toJson(o.awayTeamId),
        "homeTeamName" -> Json.toJson(o.homeTeamName),
        "homeTeamScore" -> Json.toJson(o.homeTeamScore),
        "homeTeamMessage" -> Json.toJson(o.homeTeamMessage),
        "homeTeamId" -> Json.toJson(o.homeTeamId),
        "competitionName" -> Json.toJson(o.competitionName),
        "venue" -> Json.toJson(o.venue),
        "matchId" -> Json.toJson(o.matchId),
        "mapiUrl" -> Json.toJson(o.mapiUrl),
        "importance" -> Json.toJson(o.importance),
        "topic" -> Json.toJson(o.topic),
        "phase" -> Json.toJson(o.phase),
        "eventId" -> Json.toJson(o.eventId),
        "debug" -> Json.toJson(o.debug)
      )
    )
  }
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
