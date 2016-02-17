package notification

import _root_.models.NotificationReport
import providers.ProviderError

import scalaz.\/

package object services {
  case class NotificationRejected(error: Option[ProviderError] = None) {
    override def toString = error map { e => s"Notification rejected by ${ e.providerName }, reason: ${ e.reason }" } getOrElse "Notification rejected"
  }
  type SenderResult = NotificationRejected \/ NotificationReport
}
