package registration.services.azure

import azure.{NotificationHubClient, RawWindowsRegistration}
import tracking.SubscriptionTracker

import scala.concurrent.ExecutionContext

class WindowsNotificationRegistrar(hubClient: NotificationHubClient, subscriptionTracker: SubscriptionTracker)(implicit ec: ExecutionContext)
  extends NotificationHubRegistrar(hubClient, subscriptionTracker, RawWindowsRegistration.fromMobileRegistration)(ec)
