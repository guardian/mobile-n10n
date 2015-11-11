package services

import auditor.AuditorGroupConfig
import conf.NotificationConfiguration

class Configuration extends NotificationConfiguration("registration") {
  lazy val hubEndpoint = conf.getStringProperty("gu.msnotifications.endpointUri").get
  lazy val hubSecretKeyName = conf.getStringProperty("gu.msnotifications.sharedKeyName").get
  lazy val hubSecretKey = conf.getStringProperty("gu.msnotifications.sharedKeyValue").get

  lazy val auditorConfiguration = AuditorGroupConfig(
    hosts = Set(
      conf.getStringProperty("notifications.auditor.content-notifications").get,
      conf.getStringProperty("notifications.auditor.goal-alerts").get
    )
  )
}
