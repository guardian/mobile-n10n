package notification.models.wns

import java.net.URI
import java.util.UUID

import models.NotificationType.{BreakingNews, Content, GoalAlert}
import models._
import play.api.libs.json._

sealed trait Notification {
  def id: UUID
  def `type`: NotificationType
  def title: String
  def message: String
  def thumbnailUrl: Option[URI]
  def topic: Set[Topic]
  def debug: Boolean
}

object Notification {
  import JsonUtils._
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
        case JsDefined(JsString("goal")) => GoalAlertNotification.jf.reads(json)
        case _ => JsError("Unknown notification type")
      }
    }
  }
}

case class BreakingNewsNotification(
  id: UUID,
  `type`: NotificationType = BreakingNews,
  title: String,
  message: String,
  thumbnailUrl: Option[URI],
  link: URI,
  imageUrl: Option[URI],
  topic: Set[Topic],
  debug: Boolean
) extends Notification

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
  link: URI,
  topic: Set[Topic],
  debug: Boolean
) extends Notification

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
  link: URI,
  topic: Set[Topic],
  addedTime: Option[String],
  debug: Boolean
) extends Notification

object GoalAlertNotification {
  import JsonUtils._
  implicit val jf = Json.format[GoalAlertNotification]
}