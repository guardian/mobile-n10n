package notification.models.ios

import java.net.URI
import java.util.UUID

import azure.apns.{Alert, Body, APS}
import models.{NotificationType, Topic}
import models.NotificationType.{BreakingNews, Content}
import notification.services.azure.PlatformUriType

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
  legacyLink: String
) extends Notification {

  def payload: Body = Body(
    aps = APS(
      category = Some(category),
      alert = Some(Right(message)),
      `content-available` = Some(1),
      sound = Some("default")
    ),
    customProperties = Map(
      Keys.MessageType -> `type`,
      Keys.NotificationType -> notificationType.value,
      Keys.Link -> legacyLink,
      Keys.Topics -> topics.map(_.toString).mkString(","),
      Keys.Uri -> uri.toString,
      Keys.UriType -> uriType.toString
    )
  )
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
    customProperties = Map(
      Keys.MessageType -> `type`,
      Keys.NotificationType -> notificationType.value,
      Keys.Link -> legacyLink,
      Keys.Topics -> topics.map(_.toString).mkString(","),
      Keys.Uri -> uri.toString,
      Keys.UriType -> uriType.toString
    )
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
    customProperties = Map(
      Keys.MessageType -> `type`,
      Keys.Uri -> uri.toString,
      Keys.UriType -> uriType.toString
    )
  )
}