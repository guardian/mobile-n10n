package com.gu.notifications.ec2worker

import com.gu.notifications.worker.delivery.apns.models.ApnsConfig
import com.typesafe.config.Config
import db.JdbcConfig
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.notifications.worker.ApnsWorkerConfiguration
import com.gu.notifications.worker.FcmWorkerConfiguration

class ConfigWithPrefix(config: Config, prefix: String) {
    
    def getString(key: String): String = config.getString(s"${prefix}.${key}")

    def getBoolean(key: String): Boolean = config.getBoolean(s"${prefix}.${key}")

    def getInt(key: String): Int = config.getInt(s"${prefix}.${key}")
}

object Configuration {

  def fetchConfiguration(): Config = {
    val identity = AppIdentity.whoAmI(defaultAppName = "sender-worker")
    ConfigurationLoader.load(identity) {
      case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/workers")
    }
  }

  def platform: Option[Platform] = Option(System.getenv("Platform")).flatMap(Platform.fromString)

  def confPrefixFromPlatform: String = platform match {
    case Some(p @ (Ios | Android | IosEdition | AndroidEdition | AndroidBeta)) => p.toString
    case _ => throw new IllegalStateException("No Platform environment variable defined")
  }

  def fetchApns(platform: Platform): ApnsWorkerConfiguration = {
    val config = new ConfigWithPrefix(fetchConfiguration(), platform.toString())
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
      ),
      config.getInt("apns.threadPoolSize")
    )
  }

  def fetchFirebase(platform: Platform): FcmWorkerConfiguration = {
    val config = new ConfigWithPrefix(fetchConfiguration(), platform.toString())
    FcmWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      FcmConfig(
        serviceAccountKey = config.getString("fcm.serviceAccountKey"),
        debug = config.getBoolean("fcm.debug"),
        dryRun = config.getBoolean("dryrun")
      ),
      config.getInt("fcm.threadPoolSize")
    )
  }


}
