package notification.models.ios

import java.net.URI
import java.util.UUID

import azure.apns._
import models.{NotificationType, Provider, Topic}
import models.NotificationType.{BreakingNews, Content, ElectionsAlert, FootballMatchStatus, LiveEventAlert}
import notification.services.azure.PlatformUriType
import utils.MapImplicits._

sealed trait Notification {
  def payload: Body
}

case class BreakingNewsNotification(
  id: UUID,
  notificationType: NotificationType = BreakingNews,
  `type`: String = MessageTypes.NewsAlert,
  category: String,
  message: String,
  link: URI,
  topics: Set[Topic],
  uri: URI,
  uriType: PlatformUriType,
  legacyLink: String,
  imageUrl: Option[URI]
) extends Notification {

  def payload: Body = Body(
    aps = APS(
      category = Some(category),
      alert = Some(Right(message)),
      `content-available` = Some(1),
      `mutable-content` = if (imageUrl.isDefined) Some(1) else None,
      sound = Some("default")
    ),
    customProperties = LegacyProperties(Map(
      Keys.UniqueIdentifier -> id.toString,
      Keys.Provider -> Provider.Azure,
      Keys.MessageType -> `type`,
      Keys.NotificationType -> notificationType.value,
      Keys.Link -> legacyLink,
      Keys.Topics -> topics.map(_.toString).mkString(","),
      Keys.Uri -> uri.toString,
      Keys.UriType -> uriType.toString
    ) ++ Map(Keys.ImageUrl -> imageUrl.map(_.toString)).flattenValues
  ))
}

case class ContentNotification(
  id: UUID,
  notificationType: NotificationType = Content,
  `type`: String = MessageTypes.NewsAlert,
  category: String,
  message: String,
  link: URI,
  topics: Set[Topic],
  uri: URI,
  uriType: PlatformUriType,
  legacyLink: String
) extends Notification {
  def payload: Body = Body(
    aps = APS(
      category = Some(category),
      alert = Some(Right(message)),
      `content-available` = Some(1),
      sound = Some("default")
    ),
    customProperties = LegacyProperties(Map(
      Keys.UniqueIdentifier -> id.toString,
      Keys.Provider -> Provider.Azure,
      Keys.MessageType -> `type`,
      Keys.NotificationType -> notificationType.value,
      Keys.Link -> legacyLink,
      Keys.Topics -> topics.map(_.toString).mkString(","),
      Keys.Uri -> uri.toString,
      Keys.UriType -> uriType.toString
    ))
  )
}

case class NewsstandNotification(id: UUID) extends Notification {
  def payload: Body = Body(
    aps = APS(
      alert = None,
      `content-available` = Some(1),
      sound = None
    ),
    customProperties = LegacyProperties(Map.empty)
  )
}

case class NewsstandNotificationShard(id:UUID, shard:Number) extends Notification {
  override def payload: Body = Body(
    aps = APS(
      alert = None,
      `content-available` = Some(1),
      sound = None
    ),
    customProperties = LegacyProperties(Map.empty)
  )
}

case class ElectionNotification(
  `type`: String = MessageTypes.ElectionAlert,
  notificationType: NotificationType = ElectionsAlert,
  message: String,
  id: UUID,
  title: String,
  body: String,
  richBody: String,
  democratVotes: Int,
  republicanVotes: Int,
  link: URI,
  resultsLink: URI,
  buzz: Boolean
) extends Notification {
  def payload: Body = Body(
    aps = APS(
      alert = None,
      `content-available` = Some(1),
      sound = None
    ),
    customProperties = StandardProperties(
      uniqueIdentifier = id,
      provider = Provider.Azure,
      t = `type`,
      notificationType = notificationType,
      election = Some(ElectionProperties(
        title = title,
        body = body,
        richviewbody = richBody,
        sound = if (buzz) 1 else 0,
        dem = democratVotes,
        rep = republicanVotes,
        link = link.toString,
        results = resultsLink.toString
      ))
    )
  )
}

case class LiveEventNotification(id: UUID, liveEvent: LiveEventProperties) extends Notification {
  def payload: Body = Body(
    aps = APS(
      alert = None,
      `content-available` = Some(1),
      sound = None
    ),
    customProperties = StandardProperties(
      uniqueIdentifier = id,
      provider = Provider.Azure,
      t = MessageTypes.LiveEventAlert,
      notificationType = LiveEventAlert,
      liveEvent = Some(liveEvent)
    )
  )
}

case class FootballMatchStatusNotification(
  id: UUID,
  title: String,
  body: String,
  matchStatus: FootballMatchStatusProperties,
  sound: Boolean
) extends Notification {
  def payload: Body = Body(
    aps = APS(
      alert = Some(Left(Alert(title = Some(title), body = Some(body)))),
      sound = if (sound) Some("default") else None,
      category = Some("football-match"),
      `mutable-content` = Some(1)
    ),
    customProperties = StandardProperties(
      uniqueIdentifier = id,
      provider = Provider.Azure,
      t = MessageTypes.FootballMatchStatus,
      notificationType = FootballMatchStatus,
      footballMatch = Some(matchStatus)
    )
  )
}