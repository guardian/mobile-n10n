package com.gu.notifications.worker

import com.gu.notifications.worker.delivery.apns.models.ApnsConfig
import com.typesafe.config.Config
import db.JdbcConfig
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region.EU_WEST_1

case class HarvesterConfiguration(
  jdbcConfig: JdbcConfig,
  iosLiveSqsUrl: String,
  iosEditionSqsUrl: String,
  androidLiveSqsUrl: String,
  androidEditionSqsUrl: String,
  androidBetaSqsUrl: String,
)

sealed trait WorkerConfiguration {
  def cleaningSqsUrl: String
}

case class ApnsWorkerConfiguration(
  cleaningSqsUrl: String,
  apnsConfig: ApnsConfig,
  threadPoolSize: Int
) extends WorkerConfiguration

case class FcmWorkerConfiguration(
  cleaningSqsUrl: String,
  fcmConfig: FcmConfig,
  threadPoolSize: Int,
  allowedTopicsForIndividualSend: List[String],
) extends WorkerConfiguration {
  def isIndividualSend(topics: List[String]): Boolean = 
      topics.forall(topic => allowedTopicsForIndividualSend.exists(topic.startsWith(_)))
}

case class CleanerConfiguration(jdbcConfig: JdbcConfig)

case class TopicCountsConfiguration (
  jdbcConfig: JdbcConfig,
  countThreshold: Int,
  bucketName: String,
  fileName: String
)

object Configuration {

  private def fetchConfiguration(workerName: String): Config = {
    val defaultAppName = "notification-worker"
    val identity = Option(System.getenv("MOBILE_LOCAL_DEV")) match {
      case Some(_) => DevIdentity(defaultAppName)
      case None =>
        AppIdentity
          .whoAmI(defaultAppName, DefaultCredentialsProvider.builder().build())
          .getOrElse(DevIdentity(defaultAppName))
    }
    ConfigurationLoader.load(identity) {
      case AwsIdentity(_, _, stage, region) => SSMConfigurationLocation(s"/notifications/$stage/workers/$workerName", region)
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
      if (config.hasPath(path))
        config.getString(path).split(",").toList
      else
        List()

    FcmWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      FcmConfig(
        serviceAccountKey = config.getString("fcm.serviceAccountKey"),
        debug = config.getBoolean("fcm.debug"),
        dryRun = config.getBoolean("dryrun")
      ),
      config.getInt("fcm.threadPoolSize"),
      getStringList("fcm.allowedTopicsForIndividualSend")
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
