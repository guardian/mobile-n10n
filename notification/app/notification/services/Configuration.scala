package notification.services

import _root_.azure.NotificationHubConnection
import play.api.{Configuration => PlayConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {
  lazy val defaultHub = NotificationHubConnection(
    endpoint = conf.get[String]("azure.hub.endpoint"),
    sharedAccessKeyName = conf.get[String]("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = conf.get[String]("azure.hub.sharedAccessKey")
  )

  lazy val enterpriseHub: NotificationHubConnection = getConfigurableHubConnection("enterprise")

  lazy val newsstandHub: NotificationHubConnection = getConfigurableHubConnection("newsstand.azure")

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
  lazy val dynamoTopicsTableName: String = conf.get[String]("db.dynamo.topics.table-name")
  lazy val dynamoTopicsFlushInterval: FiniteDuration = conf.getOptional[FiniteDuration]("db.dynamo.topics.flush-interval").getOrElse(60.seconds)
  lazy val frontendBaseUrl: String = conf.get[String]("frontend.baseUrl")

  lazy val disableElectionNotificationsAndroid: Boolean = conf.getOptional[Boolean]("notifications.elections.android.disabled").getOrElse(false)
  lazy val disableElectionNotificationsIOS: Boolean = conf.getOptional[Boolean]("notifications.elections.ios.disabled").getOrElse(false)

  lazy val newsstandShards: Int = conf.get[Int]("newsstand.shards")

  private def getConfigurableHubConnection(hubConfigurationName: String): NotificationHubConnection = {
    val hub = for {
      endpoint <- conf.getOptional[String](s"$hubConfigurationName.hub.endpoint")
      sharedAccessKeyName <- conf.getOptional[String](s"$hubConfigurationName.hub.sharedAccessKeyName")
      sharedAccessKey <- conf.getOptional[String](s"$hubConfigurationName.hub.sharedAccessKey")
    } yield NotificationHubConnection(endpoint = endpoint, sharedAccessKeyName = sharedAccessKeyName, sharedAccessKey = sharedAccessKey)
    hub getOrElse defaultHub
  }
}
