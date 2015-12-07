package notification.models

import models.{Notification, Topic, UserId}
import play.api.libs.json._

case class Push(notification: Notification, destination: Either[Topic, UserId]) {
  def tagQuery: Option[String] = None
}
