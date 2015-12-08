package notification.models

import models.Notification
import notification.models.Destination.Destination
import play.api.libs.json._

case class Push(notification: Notification, destination: Destination)
