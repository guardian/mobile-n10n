package notification.services

import play.api.{Configuration => PlayConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {

  lazy val apiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.secretKeys")
  lazy val electionRestrictedApiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.electionRestrictedKeys")
  lazy val frontendNewsAlertEndpoint: String = conf.get[String]("notifications.frontendNewsAlert.endpoint")
  lazy val frontendNewsAlertApiKey: String = conf.get[String]("notifications.frontendNewsAlert.apiKey")
  lazy val dynamoReportsTableName: String = conf.get[String]("db.dynamo.reports.table-name")
  lazy val dynamoScheduleTableName: String = conf.get[String]("db.dynamo.schedule.table-name")

  lazy val newsstandShards: Int = conf.get[Int]("newsstand.shards")
}
