package notification.services.fcm

import notification.models.Push

trait FCMConfigConverter[A] {
  def toFCM(push: Push): Option[A]
}
