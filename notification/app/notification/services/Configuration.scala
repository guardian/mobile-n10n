package notification.services

import _root_.azure.NotificationHubConnection
import conf.NotificationConfiguration
import scala.concurrent.duration._

class Configuration extends NotificationConfiguration("notification") {
  lazy val defaultHub = NotificationHubConnection(
    endpoint = getConfigString("azure.hub.endpoint"),
    sharedAccessKeyName = getConfigString("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = getConfigString("azure.hub.sharedAccessKey")
  )

  lazy val enterpriseHub = NotificationHubConnection(
    endpoint = getConfigString("enterprise.hub.endpoint"),
    sharedAccessKeyName = getConfigString("enterprise.hub.sharedAccessKeyName"),
    sharedAccessKey = getConfigString("enterprise.hub.sharedAccessKey")
  )

  lazy val hubSharedAccessKeyName = getConfigString("azure.hub.sharedAccessKeyName")
  lazy val hubSharedAccessKey = getConfigString("azure.hub.sharedAccessKey")
  lazy val apiKeys = conf.getStringPropertiesSplitByComma("notifications.api.secretKeys")
  lazy val electionRestrictedApiKeys = conf.getStringPropertiesSplitByComma("notifications.api.electionRestrictedKeys")
  lazy val mapiItemEndpoint = conf.getStringProperty("mapi.items.endpoint", "http://mobile-apps.guardianapis.com/items")
  lazy val debug = getConfigBoolean("notifications.api.debug")
  lazy val frontendNewsAlertEndpoint = getConfigString("notifications.frontendNewsAlert.endpoint")
  lazy val frontendNewsAlertApiKey = getConfigString("notifications.frontendNewsAlert.apiKey")
  lazy val dynamoReportsTableName = getConfigString("db.dynamo.reports.table-name")
  lazy val dynamoTopicsTableName = getConfigString("db.dynamo.topics.table-name")
  lazy val dynamoTopicsFlushInterval = getFiniteDuration("db.dynamo.topics.flush-interval").getOrElse(60.seconds)
  lazy val frontendBaseUrl = getConfigString("frontend.baseUrl")
}
