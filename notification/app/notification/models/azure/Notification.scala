package notification.models.azure

import java.util.UUID
import models.NotificationType.{GoalAlert, Content, BreakingNews}
import models._
import play.api.libs.json._
import models.JsonUtils._

sealed trait Notification {
  def id: UUID
  def `type`: NotificationType
  def title: String
  def message: String
  def thumbnailUrl: Option[URL]
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

case class BreakingNewsNotification(
  id: UUID,
  `type`: NotificationType = BreakingNews,
  title: String,
  message: String,
  thumbnailUrl: Option[URL],
  link: URL,
  imageUrl: Option[URL],
  topic: Set[Topic]
) extends Notification

object BreakingNewsNotification {
  implicit val jf = Json.format[BreakingNewsNotification]
}

case class ContentNotification(
  id: UUID,
  `type`: NotificationType = Content,
  title: String,
  message: String,
  thumbnailUrl: Option[URL],
  link: URL,
  topic: Set[Topic]
) extends Notification

object ContentNotification {
  implicit val jf = Json.format[ContentNotification]
}

case class GoalAlertNotification(
  id: UUID,
  `type`: NotificationType = GoalAlert,
  title: String,
  message: String,
  thumbnailUrl: Option[URL] = None,
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
  link: URL,
  topic: Set[Topic],
  addedTime: Option[String]
) extends Notification

object GoalAlertNotification {
  implicit val jf = Json.format[GoalAlertNotification]
}