package notification.services

import _root_.azure.NotificationHubConnection
import play.api.{Configuration => PlayConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {
  val defaultHub = NotificationHubConnection(
    endpoint = conf.get[String]("azure.hub.endpoint"),
    sharedAccessKeyName = conf.get[String]("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = conf.get[String]("azure.hub.sharedAccessKey")
  )

  val enterpriseHub: NotificationHubConnection = getConfigurableHubConnection("enterprise")

  val newsstandHub: NotificationHubConnection = getConfigurableHubConnection("newsstand.azure")

  val hubSharedAccessKeyName: String = conf.get[String]("azure.hub.sharedAccessKeyName")
  val hubSharedAccessKey: String = conf.get[String]("azure.hub.sharedAccessKey")
  val apiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.secretKeys")
  val electionRestrictedApiKeys: Seq[String] = conf.get[Seq[String]]("notifications.api.electionRestrictedKeys")
  val mapiItemEndpoint: String = conf.getOptional[String]("mapi.items.endpoint").getOrElse("http://mobile-apps.guardianapis.com/items")
  val debug: Boolean = conf.get[Boolean]("notifications.api.debug")
  val frontendNewsAlertEndpoint: String = conf.get[String]("notifications.frontendNewsAlert.endpoint")
  val frontendNewsAlertApiKey: String = conf.get[String]("notifications.frontendNewsAlert.apiKey")
  val dynamoReportsTableName: String = conf.get[String]("db.dynamo.reports.table-name")
  val dynamoTopicsTableName: String = conf.get[String]("db.dynamo.topics.table-name")
  val dynamoTopicsFlushInterval: FiniteDuration = conf.getOptional[FiniteDuration]("db.dynamo.topics.flush-interval").getOrElse(60.seconds)
  val frontendBaseUrl: String = conf.get[String]("frontend.baseUrl")

  val disableElectionNotificationsAndroid: Boolean = conf.getOptional[Boolean]("notifications.elections.android.disabled").getOrElse(false)
  val disableElectionNotificationsIOS: Boolean = conf.getOptional[Boolean]("notifications.elections.ios.disabled").getOrElse(false)

  private def getConfigurableHubConnection(hubConfigurationName: String): NotificationHubConnection = {
    val hub = for {
      endpoint <- conf.getOptional[String](s"$hubConfigurationName.hub.endpoint")
      sharedAccessKeyName <- conf.getOptional[String](s"$hubConfigurationName.hub.sharedAccessKeyName")
      sharedAccessKey <- conf.getOptional[String](s"$hubConfigurationName.hub.sharedAccessKey")
    } yield NotificationHubConnection(endpoint = endpoint, sharedAccessKeyName = sharedAccessKeyName, sharedAccessKey = sharedAccessKey)
    hub getOrElse defaultHub
  }
}
