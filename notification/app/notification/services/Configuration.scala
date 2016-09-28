package notification.services

import conf.NotificationConfiguration
import scala.concurrent.duration._

class Configuration extends NotificationConfiguration("notification") {
  lazy val hubEndpoint = getConfigString("azure.hub.endpoint")
  lazy val hubSharedAccessKeyName = getConfigString("azure.hub.sharedAccessKeyName")
  lazy val hubSharedAccessKey = getConfigString("azure.hub.sharedAccessKey")
  lazy val apiKeys = conf.getStringPropertiesSplitByComma("notifications.api.secretKeys")
  lazy val mapiItemEndpoint = conf.getStringProperty("mapi.items.endpoint", "http://mobile-apps.guardianapis.com/items")
  lazy val debug = getConfigBoolean("notifications.api.debug")
  lazy val frontendNewsAlertEndpoint = getConfigString("notifications.frontendNewsAlert.endpoint")
  lazy val frontendNewsAlertApiKey = getConfigString("notifications.frontendNewsAlert.apiKey")
  lazy val dynamoReportsTableName = getConfigString("db.dynamo.reports.table-name")
  lazy val dynamoTopicsTableName = getConfigString("db.dynamo.topics.table-name")
  lazy val dynamoTopicsFlushInterval = getFiniteDuration("db.dynamo.topics.flush-interval").getOrElse(60.seconds)
  lazy val frontendBaseUrl = getConfigString("frontend.baseUrl")
}
