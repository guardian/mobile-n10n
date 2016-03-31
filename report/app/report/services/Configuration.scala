package report.services

import conf.NotificationConfiguration

class Configuration extends NotificationConfiguration("report") {
  lazy val apiKeys = conf.getStringPropertiesSplitByComma("notifications.api.secretKeys")

  lazy val dynamoReportsTableName = getConfigString("db.dynamo.reports.table-name")
}
