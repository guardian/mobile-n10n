package com.gu.notifications.worker

import com.gu.notifications.worker.delivery.apns.models.ApnsConfig
import com.typesafe.config.Config
import db.JdbcConfig
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

case class HarvesterConfiguration(
  jdbcConfig: JdbcConfig,
  iosLiveSqsUrl: String,
  iosEditionSqsUrl: String,
  androidLiveSqsUrl: String,
  androidEditionSqsUrl: String,
  androidBetaSqsUrl: String
)

sealed trait WorkerConfiguration {
  def cleaningSqsUrl: String
}

case class ApnsWorkerConfiguration(
  cleaningSqsUrl: String,
  apnsConfig: ApnsConfig
) extends WorkerConfiguration

case class FcmWorkerConfiguration(
  cleaningSqsUrl: String,
  fcmConfig: FcmConfig
) extends WorkerConfiguration

case class CleanerConfiguration(jdbcConfig: JdbcConfig)

case class TopicCountsConfiguration (
  jdbcConfig: JdbcConfig,
  countThreshold: Int,
  bucketName: String,
  fileName: String
)

object Configuration {

  private def fetchConfiguration(workerName: String): Config = {
    val identity = AppIdentity.whoAmI(defaultAppName = "notification-worker")
    ConfigurationLoader.load(identity) {
      case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/workers/$workerName")
    }
  }

  def platform: Option[Platform] = Option(System.getenv("Platform")).flatMap(Platform.fromString)

  def confPrefixFromPlatform: String = platform match {
    case Some(p @ (Ios | Android | IosEdition | AndroidEdition | AndroidBeta)) => p.toString
    case _ => throw new IllegalStateException("No Platform environment variable defined")
  }

  private def jdbcConfig(config: Config) = JdbcConfig(
    driverClassName = "org.postgresql.Driver",
    url = config.getString("registration.db.url"),
    user = config.getString("registration.db.user"),
    password = config.getString("registration.db.password"),
    maxConnectionPoolSize = 1
  )

  def fetchHarvester(): HarvesterConfiguration = {
    val config = fetchConfiguration("harvester")
    HarvesterConfiguration(
      jdbcConfig = jdbcConfig(config),
      iosLiveSqsUrl = config.getString("iosLiveSqsCdkUrl"),
      iosEditionSqsUrl = config.getString("iosEditionSqsCdkUrl"),
      androidLiveSqsUrl = config.getString("androidLiveSqsCdkUrl"),
      androidEditionSqsUrl = config.getString("androidEditionSqsCdkUrl"),
      androidBetaSqsUrl = config.getString("androidBetaSqsCdkUrl")
    )
  }

  def fetchApns(): ApnsWorkerConfiguration = {
    val config = fetchConfiguration(confPrefixFromPlatform)
    ApnsWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      ApnsConfig(
        teamId = config.getString("apns.teamId"),
        bundleId = config.getString("apns.bundleId"),
        keyId = config.getString("apns.keyId"),
        certificate = config.getString("apns.certificate"),
        mapiBaseUrl = config.getString("mapi.baseUrl"),
        sendingToProdServer = config.getBoolean("apns.sendingToProdServer"),
        dryRun = config.getBoolean("dryrun")
      )
    )
  }

  def fetchFirebase(): FcmWorkerConfiguration = {
    val config = fetchConfiguration(confPrefixFromPlatform)
    FcmWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      FcmConfig(
        serviceAccountKey = config.getString("fcm.serviceAccountKey"),
        debug = config.getBoolean("fcm.debug"),
        dryRun = config.getBoolean("dryrun")
      )
    )
  }

  def fetchCleaner(): CleanerConfiguration = {
    val config = fetchConfiguration("cleaner")
    CleanerConfiguration(
      jdbcConfig(config)
    )
  }

  def fetchTopicCounter(): TopicCountsConfiguration = {
    val config = fetchConfiguration("topicCounter")
    TopicCountsConfiguration(
      jdbcConfig(config),
      config.getInt("countThreshold"),
      config.getString("bucket"),
      config.getString("fileName")
    )
  }
}
