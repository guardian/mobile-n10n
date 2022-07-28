package com.gu.notifications.workerlambda

import com.gu.notifications.workerlambda.delivery.apns.models.ApnsConfig
import com.typesafe.config.Config
import com.gu.notifications.workerlambda.delivery.fcm.models.FcmConfig
import com.gu.notifications.workerlambda.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

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
}
