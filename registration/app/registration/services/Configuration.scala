package registration.services

import auditor.{AuditorGroupConfig, ApiConfig}
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
    contentApiConfig = ApiConfig(
      apiKey = getConfigString("notifications.auditor.contentApi.apiKey"),
      url = getConfigString("notifications.auditor.contentApi.url")
    ),
    paApiConfig = ApiConfig(
      apiKey = getConfigString("notifications.auditor.paApi.apiKey"),
      url = getConfigString("notifications.auditor.paApi.url")
    )
  )
  lazy val maxTopics = getConfigInt("notifications.max_topics")
  lazy val dynamoTopicsTableName = getConfigString("db.dynamo.topics.table-name")
  lazy val dynamoTopicsFlushInterval = getFiniteDuration("db.dynamo.topics.flush-interval").getOrElse(60.seconds)

  lazy val defaultTimeout = getFiniteDuration("routes.defaultTimeout").getOrElse(30.seconds)
}
