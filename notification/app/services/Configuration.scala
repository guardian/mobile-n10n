package services

import conf.NotificationConfiguration

case class NotificationHubConfiguration(
  endpointUri: String,
  hubName: String,
  sharedKeyName: String,
  sharedKeyValue: String
)

final class Configuration extends NotificationConfiguration("notification") {
  lazy val notificationHubConfiguration = NotificationHubConfiguration(
    endpointUri= conf.getStringProperty("gu.msnotifications.endpointUri").get,
    hubName = conf.getStringProperty("gu.msnotifications.hubname").get,
    sharedKeyName = conf.getStringProperty("gu.msnotifications.sharedKeyName").get,
    sharedKeyValue = conf.getStringProperty("gu.msnotifications.sharedKeyValue").get
  )

  lazy val apiKey = conf.getStringProperty("gu.msnotifications.admin-api-key")
}
