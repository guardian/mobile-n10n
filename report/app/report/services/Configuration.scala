package report.services

import conf.NotificationConfiguration

class Configuration extends NotificationConfiguration("report") {
  lazy val apiKey = conf.getStringProperty("notifications.api.secretKey")
  lazy val dynamoReportsTableName = getConfigString("db.dynamo.reports.table-name")
}
