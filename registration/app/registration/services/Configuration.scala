package registration.services

import play.api.{Configuration => PlayConfig}
import registration.auditor.{AuditorApiConfig, AuditorGroupConfig}

import scala.concurrent.duration._

class Configuration(conf: PlayConfig) {

  lazy val auditorConfiguration = AuditorGroupConfig(
    contentApiConfig = AuditorApiConfig(
      apiKey = conf.get[String]("notifications.auditor.contentApi.apiKey"),
      url = conf.get[String]("notifications.auditor.contentApi.url")
    ),
    paApiConfig = AuditorApiConfig(
      apiKey = conf.get[String]("notifications.auditor.paApi.apiKey"),
      url = conf.get[String]("notifications.auditor.paApi.url")
    )
  )
  lazy val newsstandShards: Int = conf.get[Int]("newsstand.shards")
  lazy val maxTopics: Int = conf.get[Int]("notifications.max_topics")

  lazy val defaultTimeout: FiniteDuration = conf.getOptional[FiniteDuration]("routes.defaultTimeout").getOrElse(30.seconds)

}
