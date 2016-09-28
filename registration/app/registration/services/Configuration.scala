package registration.services

import auditor.AuditorGroupConfig
import _root_.azure.NotificationHubConnection
import conf.NotificationConfiguration

import scala.concurrent.duration._

class Configuration extends NotificationConfiguration("registration") {
  lazy val legacyNotficationsEndpoint = getConfigString("legacy_notifications.endpoint")

  lazy val defaultHub = NotificationHubConnection(
    endpoint = getConfigString("azure.hub.endpoint"),
    sharedAccessKeyName = getConfigString("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = getConfigString("azure.hub.sharedAccessKey")
  )

  lazy val newsstandHub = NotificationHubConnection(
    endpoint = getConfigString("newsstand.azure.hub.endpoint"),
    sharedAccessKeyName = getConfigString("newsstand.azure.hub.sharedAccessKeyName"),
    sharedAccessKey = getConfigString("newsstand.azure.hub.sharedAccessKey")
  )

  lazy val auditorConfiguration = AuditorGroupConfig(
    hosts = Set(
      getConfigString("notifications.auditor.contentNotifications"),
      getConfigString("notifications.auditor.goalAlerts")
    )
  )
  lazy val maxTopics = getConfigInt("notifications.max_topics", 200) // scalastyle:off magic.number
  lazy val dynamoTopicsTableName = getConfigString("db.dynamo.topics.table-name")
  lazy val dynamoTopicsFlushInterval = getFiniteDuration("db.dynamo.topics.flush-interval").getOrElse(60.seconds)

  lazy val defaultTimeout = getFiniteDuration("routes.defaultTimeout").getOrElse(30.seconds)
}
