package registration.services.azure

import azure.{NotificationHubClient, RawGCMRegistration}
import tracking.SubscriptionTracker

import scala.concurrent.ExecutionContext

class GCMNotificationRegistrar(hubClient: NotificationHubClient, subscriptionTracker: SubscriptionTracker)(implicit ec: ExecutionContext)
  extends NotificationHubRegistrar(hubClient, subscriptionTracker, RawGCMRegistration.fromMobileRegistration)(ec)
