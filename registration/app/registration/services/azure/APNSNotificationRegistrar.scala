package registration.services.azure

import azure.{NotificationHubClient, RawAPNSRegistration}
import tracking.SubscriptionTracker

import scala.concurrent.ExecutionContext

class APNSNotificationRegistrar(hubClient: NotificationHubClient, subscriptionTracker: SubscriptionTracker)(implicit ec: ExecutionContext)
  extends NotificationHubRegistrar(hubClient, subscriptionTracker, RawAPNSRegistration.fromMobileRegistration)(ec)