package providers

import error.NotificationsError

trait ProviderError extends NotificationsError {
  def providerName: String
}
