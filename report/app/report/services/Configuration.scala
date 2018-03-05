package report.services

import azure.NotificationHubConnection
import play.api.{Configuration => PlayConfig}

class Configuration(conf: PlayConfig) {
  lazy val apiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.secretKeys")
  lazy val electionRestrictedApiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.electionRestrictedKeys")
  lazy val reportsOnlyApiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.reportsOnlyKeys")
  lazy val dynamoReportsTableName: String = conf.get[String]("db.dynamo.reports.table-name")

  lazy val defaultHub = NotificationHubConnection(
    endpoint = conf.get[String]("azure.hub.endpoint"),
    sharedAccessKeyName = conf.get[String]("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = conf.get[String]("azure.hub.sharedAccessKey")
  )
}
