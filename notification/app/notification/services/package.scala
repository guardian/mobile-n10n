package notification

import _root_.models.SenderReport
import error.NotificationsError

package object services {
  trait SenderError extends NotificationsError {
    def senderName: String
    override def toString: String = s"Notification rejected by $senderName, reason: $reason"
  }

  object Senders {
    val AzureNotificationsHub = "Azure Notifications Hub"
    val FrontendAlerts = "Frontend Alerts Sender"
  }

  type SenderResult = Either[SenderError, SenderReport]
}
