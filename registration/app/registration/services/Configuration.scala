package registration.services

import auditor.{AuditorGroupConfig, ApiConfig}
import _root_.azure.NotificationHubConnection
import play.api.{Configuration => PlayConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {
  lazy val defaultHub = NotificationHubConnection(
    endpoint = conf.get[String]("azure.hub.endpoint"),
    sharedAccessKeyName = conf.get[String]("azure.hub.sharedAccessKeyName"),
    sharedAccessKey = conf.get[String]("azure.hub.sharedAccessKey")
  )

  lazy val newsstandHub = NotificationHubConnection(
    endpoint = conf.get[String]("newsstand.azure.hub.endpoint"),
    sharedAccessKeyName = conf.get[String]("newsstand.azure.hub.sharedAccessKeyName"),
    sharedAccessKey = conf.get[String]("newsstand.azure.hub.sharedAccessKey")
  )

  lazy val auditorConfiguration = AuditorGroupConfig(
    contentApiConfig = ApiConfig(
      apiKey = conf.get[String]("notifications.auditor.contentApi.apiKey"),
      url = conf.get[String]("notifications.auditor.contentApi.url")
    ),
    paApiConfig = ApiConfig(
      apiKey = conf.get[String]("notifications.auditor.paApi.apiKey"),
      url = conf.get[String]("notifications.auditor.paApi.url")
    )
  )
  lazy val newsstandShards: Int = conf.get[Int]("newsstand.shards")
  lazy val maxTopics: Int = conf.get[Int]("notifications.max_topics")
  lazy val dynamoTopicsTableName: String = conf.get[String]("db.dynamo.topics.table-name")
  lazy val dynamoTopicsFlushInterval: FiniteDuration = conf.getOptional[FiniteDuration]("db.dynamo.topics.flush-interval").getOrElse(60.seconds)

  lazy val defaultTimeout: FiniteDuration = conf.getOptional[FiniteDuration]("routes.defaultTimeout").getOrElse(30.seconds)
}
