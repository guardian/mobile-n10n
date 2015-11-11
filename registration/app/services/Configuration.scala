package services

import auditor.AuditorGroupConfig
import conf.NotificationConfiguration

case class NotificationHubConfiguration(
  endpointUri: String,
  hubName: String,
  sharedKeyName: String,
  sharedKeyValue: String
)

class Configuration extends NotificationConfiguration("registration") {

  lazy val notificationHubConfiguration = NotificationHubConfiguration(
    endpointUri= conf.getStringProperty("gu.msnotifications.endpointUri").get,
    hubName = conf.getStringProperty("gu.msnotifications.hubname").get,
    sharedKeyName = conf.getStringProperty("gu.msnotifications.sharedKeyName").get,
    sharedKeyValue = conf.getStringProperty("gu.msnotifications.sharedKeyValue").get
  )

  lazy val auditorConfiguration = AuditorGroupConfig(
    hosts = Set(
      conf.getStringProperty("notifications.auditor.content-notifications").get,
      conf.getStringProperty("notifications.auditor.goal-alerts").get
    )
  )
}
