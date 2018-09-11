package registration.services.azure

import azure.{NotificationHubClient, RawGCMRegistration}
import metrics.Metrics
import tracking.SubscriptionTracker

import scala.concurrent.ExecutionContext

class GCMNotificationRegistrar(hubClient: NotificationHubClient, subscriptionTracker: SubscriptionTracker, metrics: Metrics)(implicit ec: ExecutionContext)
  extends NotificationHubRegistrar(hubClient, subscriptionTracker, RawGCMRegistration.fromMobileRegistration, metrics)(ec)
