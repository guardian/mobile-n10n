package report.services

import azure.NotificationHubConnection
import conf.NotificationConfiguration

class Configuration extends NotificationConfiguration("report") {
  lazy val apiKeys = conf.getStringPropertiesSplitByComma("notifications.api.secretKeys")
  lazy val dynamoReportsTableName = getConfigString("db.dynamo.reports.table-name")

  lazy val defaultHub = NotificationHubConnection(
    endpoint = getConfigString("azure.hub.endpoint"),
    sharedAccessKeyName = getConfigString("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = getConfigString("azure.hub.sharedAccessKey")
  )
}
