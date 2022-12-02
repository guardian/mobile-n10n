package com.gu.notifications.worker

import com.gu.notifications.worker.delivery.apns.models.ApnsConfig
import com.typesafe.config.Config
import db.JdbcConfig
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

import scala.jdk.CollectionConverters.CollectionHasAsScala

case class HarvesterConfiguration(
  jdbcConfig: JdbcConfig,
  iosLiveSqsUrl: String,
  iosEditionSqsUrl: String,
  androidLiveSqsUrl: String,
  androidEditionSqsUrl: String,
  androidBetaSqsUrl: String,
  iosLiveSqsEc2Url: String,
  iosEditionSqsEc2Url: String,
  androidLiveSqsEc2Url: String,
  androidEditionSqsEc2Url: String,
  androidBetaSqsEc2Url: String,
  allowedTopicsForEc2Sender: List[String]
)

sealed trait WorkerConfiguration {
  def cleaningSqsUrl: String
}

case class ApnsWorkerConfiguration(
  cleaningSqsUrl: String,
  sqsUrl: String,
  sqsName: String,
  apnsConfig: ApnsConfig,
  threadPoolSize: Int
) extends WorkerConfiguration

case class FcmWorkerConfiguration(
  cleaningSqsUrl: String,
  sqsUrl: String,
  sqsName: String,
  fcmConfig: FcmConfig,
  threadPoolSize: Int,
  allowedTopicsForBatchSend: List[String],
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
      androidBetaSqsUrl = config.getString("androidBetaSqsCdkUrl"),
      iosLiveSqsEc2Url = config.getString("iosLiveSqsEc2Url"),
      iosEditionSqsEc2Url = config.getString("iosEditionSqsEc2Url"),
      androidLiveSqsEc2Url = config.getString("androidLiveSqsEc2Url"),
      androidEditionSqsEc2Url = config.getString("androidEditionSqsEc2Url"),
      androidBetaSqsEc2Url = config.getString("androidBetaSqsEc2Url"),
      allowedTopicsForEc2Sender = if (config.hasPath("allowedTopicsForEc2Sender")) config.getString("allowedTopicsForEc2Sender").split(",").toList else List()
    )
  }

  def fetchApns(): ApnsWorkerConfiguration = {
    val config = fetchConfiguration(confPrefixFromPlatform)
    ApnsWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      config.getString("sqsUrl"),
      config.getString("sqsName"),
      ApnsConfig(
        teamId = config.getString("apns.teamId"),
        bundleId = config.getString("apns.bundleId"),
        keyId = config.getString("apns.keyId"),
        certificate = config.getString("apns.certificate"),
        mapiBaseUrl = config.getString("mapi.baseUrl"),
        sendingToProdServer = config.getBoolean("apns.sendingToProdServer"),
        dryRun = config.getBoolean("dryrun"),
        concurrentPushyConnections = config.getInt("apns.concurrentPushyConnections"),
        maxConcurrency = config.getInt("apns.maxConcurrency"),
      ),
      config.getInt("apns.threadPoolSize")
    )
  }

  def fetchFirebase(): FcmWorkerConfiguration = {
    val config = fetchConfiguration(confPrefixFromPlatform)

    def getStringList(path: String): List[String] =
      config.getString(path).split(",").toList

    FcmWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      config.getString("sqsUrl"),
      config.getString("sqsName"),
      FcmConfig(
        serviceAccountKey = config.getString("fcm.serviceAccountKey"),
        debug = config.getBoolean("fcm.debug"),
        dryRun = config.getBoolean("dryrun")
      ),
      config.getInt("fcm.threadPoolSize"),
      getStringList("fcm.allowedTopicsForBatchSend")
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
