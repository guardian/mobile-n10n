package notification.services

import play.api.{Configuration => PlayConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {

  lazy val hubSharedAccessKeyName: String = conf.get[String]("azure.hub.sharedAccessKeyName")
  lazy val hubSharedAccessKey: String = conf.get[String]("azure.hub.sharedAccessKey")
  lazy val apiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.secretKeys")
  lazy val electionRestrictedApiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.electionRestrictedKeys")
  lazy val mapiItemEndpoint: String = conf.getOptional[String]("mapi.items.endpoint").getOrElse("http://mobile-apps.guardianapis.com/items")
  lazy val debug: Boolean = conf.get[Boolean]("notifications.api.debug")
  lazy val frontendNewsAlertEndpoint: String = conf.get[String]("notifications.frontendNewsAlert.endpoint")
  lazy val frontendNewsAlertApiKey: String = conf.get[String]("notifications.frontendNewsAlert.apiKey")
  lazy val dynamoReportsTableName: String = conf.get[String]("db.dynamo.reports.table-name")
  lazy val dynamoScheduleTableName: String = conf.get[String]("db.dynamo.schedule.table-name")
  lazy val frontendBaseUrl: String = conf.get[String]("frontend.baseUrl")

  lazy val disableElectionNotificationsAndroid: Boolean = conf.getOptional[Boolean]("notifications.elections.android.disabled").getOrElse(false)
  lazy val disableElectionNotificationsIOS: Boolean = conf.getOptional[Boolean]("notifications.elections.ios.disabled").getOrElse(false)

  lazy val newsstandShards: Int = conf.get[Int]("newsstand.shards")

  lazy val firebaseServiceAccountKey: String = conf.get[String]("notifications.firebase.serviceAccountKey")
  lazy val firebaseDatabaseUrl: String = conf.get[String]("notifications.firebase.databaseUrl")
}
