package report.services

import azure.NotificationHubConnection
import play.api.{Configuration => PlayConfig}

class Configuration(conf: PlayConfig) {
  val apiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.secretKeys")
  val electionRestrictedApiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.electionRestrictedKeys")
  val reportsOnlyApiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.reportsOnlyKeys")
  val dynamoReportsTableName: String = conf.get[String]("db.dynamo.reports.table-name")

  val defaultHub = NotificationHubConnection(
    endpoint = conf.get[String]("azure.hub.endpoint"),
    sharedAccessKeyName = conf.get[String]("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = conf.get[String]("azure.hub.sharedAccessKey")
  )
}
