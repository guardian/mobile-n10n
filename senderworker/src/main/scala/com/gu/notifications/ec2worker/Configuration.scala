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
      case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/ec2workers")
    }
  }

  def fetchApns(appConfig: Config, platform: Platform): ApnsWorkerConfiguration = {
    val config = new ConfigWithPrefix(appConfig, platform.toString())
    ApnsWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      config.getString("sqsEc2Url"),
      config.getString("sqsEc2Name"),
      ApnsConfig(
        teamId = config.getString("apns.teamId"),
        bundleId = config.getString("apns.bundleId"),
        keyId = config.getString("apns.keyId"),
        certificate = config.getString("apns.certificate"),
        mapiBaseUrl = config.getString("mapi.baseUrl"),
        sendingToProdServer = config.getBoolean("apns.sendingToProdServer"),
        dryRun = config.getBoolean("dryrun")
      ),
      config.getInt("apns.threadPoolSize"),
      config.getInt("batchsize"),
    )
  }

  def fetchFirebase(appConfig: Config, platform: Platform): FcmWorkerConfiguration = {
    val config = new ConfigWithPrefix(appConfig, platform.toString())

    def getStringList(path: String): List[String] =
      config.getString(path).split(",").toList

    FcmWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      config.getString("sqsEc2Url"),
      config.getString("sqsEc2Name"),
      FcmConfig(
        serviceAccountKey = config.getString("fcm.serviceAccountKey"),
        debug = config.getBoolean("fcm.debug"),
        dryRun = config.getBoolean("dryrun")
      ),
      config.getInt("fcm.threadPoolSize"),
      config.getInt("batchsize"),
      getStringList("fcm.allowedTopicsForBatchSend")
    )
  }


}
