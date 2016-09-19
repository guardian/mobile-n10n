package registration.services

import auditor.AuditorGroupConfig
import conf.NotificationConfiguration
import scala.concurrent.duration._

class Configuration extends NotificationConfiguration("registration") {
  lazy val legacyNotficationsEndpoint = getConfigString("legacy_notifications.endpoint")
  lazy val hubEndpoint = getConfigString("azure.hub.endpoint")
  lazy val hubSharedAccessKeyName = getConfigString("azure.hub.sharedAccessKeyName")
  lazy val hubSharedAccessKey = getConfigString("azure.hub.sharedAccessKey")
  lazy val auditorConfiguration = AuditorGroupConfig(
    hosts = Set(
      getConfigString("notifications.auditor.contentNotifications"),
      getConfigString("notifications.auditor.goalAlerts")
    )
  )
  lazy val dynamoTopicsTableName = getConfigString("db.dynamo.topics.table-name")
  lazy val dynamoTopicsFlushInterval = getFiniteDuration("db.dynamo.topics.flush-interval").getOrElse(60.seconds)
}
