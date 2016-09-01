package notification

import _root_.models.SenderReport
import error.NotificationsError

import cats.data.Xor

package object services {
  trait SenderError extends NotificationsError {
    def senderName: String
  }

  case class NotificationRejected(error: Option[SenderError] = None) {
    override def toString: String = error map { e =>
      s"Notification rejected by ${ e.senderName }, reason: ${ e.reason }"
    } getOrElse "Notification rejected"
  }

  object Senders {
    val AzureNotificationsHub = "Azure Notifications Hub"
    val FrontendAlerts = "Frontend Alerts Sender"
  }

  type SenderResult = NotificationRejected Xor SenderReport
}
