package registration.services.azure

import azure.{NotificationHubClient, RawGCMRegistration}
import metrics.Metrics
import models.DeviceToken
import tracking.SubscriptionTracker

import scala.concurrent.ExecutionContext

class GCMNotificationRegistrar(hubClient: NotificationHubClient, subscriptionTracker: SubscriptionTracker, metrics: Metrics)(implicit ec: ExecutionContext)
  extends NotificationHubRegistrar(hubClient, subscriptionTracker, RawGCMRegistration.fromMobileRegistration, metrics)(ec) {
  // forcing the FCM token on azure as all devices have migrated to azure
  override def token(deviceToken: DeviceToken): String = deviceToken.fcmToken
}
