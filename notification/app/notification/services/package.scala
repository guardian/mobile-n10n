package notification

import _root_.models.SenderReport
import error.NotificationsError

import scalaz.\/

package object services {
  trait SenderError extends NotificationsError {
    def senderName: String
    def underlying: Option[NotificationsError]
  }

  case class NotificationRejected(error: Option[SenderError] = None) {
    override def toString: String = error map { e =>
      s"Notification rejected by ${ e.senderName }, reason: ${ e.reason }"
    } getOrElse "Notification rejected"
  }

  object Senders {
    val Windows = "Windows Notification Sender"
    val FrontendAlerts = "Frontend Alerts Sender"
  }

  type SenderResult = NotificationRejected \/ SenderReport
}
