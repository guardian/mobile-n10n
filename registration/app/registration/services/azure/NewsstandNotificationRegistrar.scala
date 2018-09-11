package registration.services.azure

import azure.{NotificationHubClient, RawAPNSRegistration}
import metrics.Metrics
import tracking.SubscriptionTracker

import scala.concurrent.ExecutionContext

class NewsstandNotificationRegistrar(hubClient: NotificationHubClient, subscriptionTracker: SubscriptionTracker, metrics: Metrics)(implicit ec: ExecutionContext)
  extends NotificationHubRegistrar(hubClient, subscriptionTracker, RawAPNSRegistration.fromMobileRegistration, metrics)(ec)