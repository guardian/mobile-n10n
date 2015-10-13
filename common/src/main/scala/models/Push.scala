package models

import play.api.libs.json._
import JsonUtils._

case class Push(notification: Notification, destination: Either[Topic, UserId]) {
  def tagQuery: Option[String] = None
}

object Push {
  implicit val jf = Json.format[Push]
}
