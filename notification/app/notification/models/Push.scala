package notification.models

import models.{Notification, Topic}

case class Push(notification: Notification, destination: Set[Topic], avoidGuardianProvider: Option[Boolean] = None)
