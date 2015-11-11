package services

import auditor.AuditorGroupConfig
import conf.NotificationConfiguration

class Configuration extends NotificationConfiguration("registration") {
  lazy val hubEndpoint = getConfigString("azure.hub.endpoint")
  lazy val hubSecretKeyName = getConfigString("azure.hub.sharedAccessKeyName")
  lazy val hubSecretKey = getConfigString("azure.hub.sharedAccessKey")
  lazy val auditorConfiguration = AuditorGroupConfig(
    hosts = Set(
      getConfigString("notifications.auditor.contentNotifications"),
      getConfigString("notifications.auditor.goalAlerts")
    )
  )
}
