package registration.services

import auditor.{AuditorGroupConfig, ApiConfig}
import play.api.{Configuration => PlayConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {

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

  lazy val defaultTimeout: FiniteDuration = conf.getOptional[FiniteDuration]("routes.defaultTimeout").getOrElse(30.seconds)

  lazy val firebaseServerKey: String = conf.get[String]("notifications.firebase.serverKey")

  lazy val firebaseServiceAccountKey: String = conf.get[String]("notifications.firebase.serviceAccountKey")
  lazy val firebaseDatabaseUrl: String = conf.get[String]("notifications.firebase.databaseUrl")
}
