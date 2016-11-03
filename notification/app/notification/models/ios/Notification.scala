package notification.models.ios

import java.net.URI
import java.util.UUID

import azure.apns._
import models.{NotificationType, Topic}
import models.NotificationType.{BreakingNews, Content}
import notification.services.azure.PlatformUriType
import utils.MapImplicits._
import azure.apns.LegacyProperties._

sealed trait Notification {
  def payload: Body
}

case class BreakingNewsNotification(
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
      `mutable-content` = None,
      sound = Some("default")
    ),
    customProperties = LegacyProperties(Map(
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
      Keys.MessageType -> `type`,
      Keys.NotificationType -> notificationType.value,
      Keys.Link -> legacyLink,
      Keys.Topics -> topics.map(_.toString).mkString(","),
      Keys.Uri -> uri.toString,
      Keys.UriType -> uriType.toString
    ))
  )
}

case class GoalAlertNotification(
  `type`: String = MessageTypes.GoalAlert,
  message: String,
  id: UUID,
  uri: URI,
  uriType: PlatformUriType,
  debug: Boolean
) extends Notification {
  def payload: Body = Body(
    aps = APS(
      alert = Some(Right(message)),
      `content-available` = Some(1),
      sound = Some("default")
    ),
    customProperties = LegacyProperties(Map(
      Keys.MessageType -> `type`,
      Keys.Uri -> uri.toString,
      Keys.UriType -> uriType.toString
    ))
  )
}

case class ElectionNotification(
  `type`: String = MessageTypes.ElectionAlert,
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
      t = `type`,
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