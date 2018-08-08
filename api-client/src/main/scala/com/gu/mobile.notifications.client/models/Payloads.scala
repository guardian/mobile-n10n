package com.gu.mobile.notifications.client.models

import java.net.URI
import java.util.UUID

import com.gu.mobile.notifications.client.lib.JsonFormatsHelper._
import com.gu.mobile.notifications.client.models.Importance.Importance
import play.api.libs.json._
import NotificationPayloadType._
sealed case class GuardianItemType(mobileAggregatorPrefix: String)
object GuardianItemType {
  implicit val jf = Json.writes[GuardianItemType]
}

object GITSection extends GuardianItemType("section")
object GITTag extends GuardianItemType("latest")
object GITContent extends GuardianItemType("item-trimmed")

sealed trait Link
object Link {
  implicit val jf = new Writes[Link] {
    override def writes(o: Link): JsValue = o match {
      case l: ExternalLink => ExternalLink.jf.writes(l)
      case l: GuardianLinkDetails => GuardianLinkDetails.jf.writes(l)
    }
  }
}

object ExternalLink { implicit val jf = Json.writes[ExternalLink] }
case class ExternalLink(url: String) extends Link {
  override val toString = url
}
case class GuardianLinkDetails(
  contentApiId: String,
  shortUrl: Option[String],
  title: String,
  thumbnail: Option[String],
  git: GuardianItemType,
  blockId: Option[String] = None) extends Link {
  val webUrl = s"http://www.theguardian.com/$contentApiId"
  override val toString = webUrl
}

object GuardianLinkDetails {
  implicit val jf = Json.writes[GuardianLinkDetails]
}

sealed trait GoalType
object OwnGoalType extends GoalType
object PenaltyGoalType extends GoalType
object DefaultGoalType extends GoalType

object GoalType {
  implicit val jf = new Writes[GoalType] {
    override def writes(o: GoalType): JsValue = o match {
      case OwnGoalType => JsString("Own")
      case PenaltyGoalType => JsString("Penalty")
      case DefaultGoalType => JsString("Default")
    }
  }
}

sealed trait NotificationPayload {
  def id: UUID
  def title: String
  def `type`: NotificationPayloadType
  def message: String
  def thumbnailUrl: Option[URI]
  def sender: String
  def importance: Importance
  def topic: List[Topic]
  def debug: Boolean
}

object NotificationPayload {
  implicit val jf = new Writes[NotificationPayload] {
    override def writes(o: NotificationPayload): JsValue = o match {
      case n: BreakingNewsPayload => BreakingNewsPayload.jf.writes(n)
      case n: ContentAlertPayload => ContentAlertPayload.jf.writes(n)
      case n: FootballMatchStatusPayload => FootballMatchStatusPayload.jf.writes(n)
    }
  }
}
sealed trait NotificationWithLink extends NotificationPayload {
  def link: Link
}

object BreakingNewsPayload { val jf = Json.writes[BreakingNewsPayload] withTypeString BreakingNews.toString }
case class BreakingNewsPayload(
  id: UUID = UUID.randomUUID,
  title: String = "The Guardian",
  message: String,
  thumbnailUrl: Option[URI],
  sender: String,
  link: Link,
  imageUrl: Option[URI],
  importance: Importance,
  topic: List[Topic],
  debug: Boolean
) extends NotificationWithLink {
  val `type` = BreakingNews
}

object ContentAlertPayload {
  implicit val jf = new Writes[ContentAlertPayload] {
    override def writes(o: ContentAlertPayload) = (Json.writes[ContentAlertPayload] withAdditionalStringFields Map("type" -> ContentAlert.toString, "id" -> o.id.toString)).writes(o)
  }
}

case class ContentAlertPayload(
  title: String,
  message: String,
  thumbnailUrl: Option[URI],
  sender: String,
  link: Link,
  imageUrl: Option[URI] = None,
  importance: Importance,
  topic: List[Topic],
  debug: Boolean
) extends NotificationWithLink with derivedId {
  val `type` = ContentAlert

  override val derivedId: String = {
    def newContentIdentifier(contentApiId: String) = s"contentNotifications/newArticle/$contentApiId"
    def newBlockIdentifier(contentApiId: String, blockId: String) = s"contentNotifications/newBlock/$contentApiId/$blockId"

    val contentCoordinates = link match {
      case GuardianLinkDetails(contentApiId, _, _, _, _, blockId) => (Some(contentApiId), blockId)
      case _ => (None, None)
    }

    contentCoordinates match {
      case (Some(contentApiId), Some(blockId)) => newBlockIdentifier(contentApiId, blockId)
      case (Some(contentApiId), None) => newContentIdentifier(contentApiId)
      case (None, _) => UUID.randomUUID.toString
    }
  }
}

object FootballMatchStatusPayload {
  implicit val jf = new Writes[FootballMatchStatusPayload] {
    // more than 22 fields so I have to define that manually
    override def writes(o: FootballMatchStatusPayload): JsValue = Json.obj(
      "id" -> o.id,
      "type" -> FootballMatchStatus.toString,
      "title" -> o.title,
      "message" -> o.message,
      "thumbnailUrl" -> o.thumbnailUrl,
      "sender" -> o.sender,
      "awayTeamName" -> o.awayTeamName,
      "awayTeamScore" -> o.awayTeamScore,
      "awayTeamMessage" -> o.awayTeamMessage,
      "awayTeamId" -> o.awayTeamId,
      "homeTeamName" -> o.homeTeamName,
      "homeTeamScore" -> o.homeTeamScore,
      "homeTeamMessage" -> o.homeTeamMessage,
      "homeTeamId" -> o.homeTeamId,
      "competitionName" -> o.competitionName,
      "venue" -> o.venue,
      "matchId" -> o.matchId,
      "matchInfoUri" -> o.matchInfoUri,
      "articleUri" -> o.articleUri,
      "importance" -> o.importance,
      "topic" -> o.topic,
      "matchStatus" -> o.matchStatus,
      "eventId" -> o.eventId,
      "debug" -> o.debug
    )
  }
}
case class FootballMatchStatusPayload(
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
) extends NotificationPayload with derivedId {
  val `type` = FootballMatchStatus
  override val derivedId = s"football-match-status/$matchId/$eventId"
}
trait derivedId {
  val derivedId: String
  lazy val id = UUID.nameUUIDFromBytes(derivedId.getBytes)
}