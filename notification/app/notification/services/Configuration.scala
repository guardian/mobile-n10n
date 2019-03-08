package notification.services

import play.api.{Configuration => PlayConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {

  lazy val apiKeys: Set[String] = conf.get[Seq[String]]("notifications.api.secretKeys").toSet
  lazy val frontendNewsAlertEndpoint: String = conf.get[String]("notifications.frontendNewsAlert.endpoint")
  lazy val frontendNewsAlertApiKey: String = conf.get[String]("notifications.frontendNewsAlert.apiKey")
  lazy val dynamoReportsTableName: String = conf.get[String]("db.dynamo.reports.table-name")
  lazy val dynamoScheduleTableName: String = conf.get[String]("db.dynamo.schedule.table-name")
  lazy val newsstandRestrictedApiKeys: Set[String] = conf.get[Seq[String]]("notifications.api.newsstandRestrictedKeys").toSet

  lazy val newsstandShards: Int = conf.get[Int]("newsstand.shards")
}
