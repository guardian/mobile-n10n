package com.gu.notifications.worker

import apnsworker.models.ApnsConfig
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.typesafe.config.Config
import db.JdbcConfig
import fcmworker.models.FcmConfig

case class ApnsWorkerConfiguration(jdbcConfig: JdbcConfig, apnsConfig: ApnsConfig)
case class FcmWorkerConfiguration(jdbcConfig: JdbcConfig, fcmConfig: FcmConfig)

object Configuration {

  private def fetchConfiguration(): Config = {
    val identity = AppIdentity.whoAmI(defaultAppName = "notification-worker")
    ConfigurationLoader.load(identity) {
      case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/workers")
    }
  }

  private def jdbcConfig(config: Config) = JdbcConfig(
    driverClassName = "org.postgresql.Driver",
    url = config.getString("registration.db.url"),
    user = config.getString("registration.db.user"),
    password = config.getString("registration.db.password"),
    fixedThreadPool = config.getInt("registration.db.threads")
  )

  def fetchApns(): ApnsWorkerConfiguration = {
    val config = fetchConfiguration()
    ApnsWorkerConfiguration(
      jdbcConfig(config),
      ApnsConfig(
        teamId = config.getString("apns.teamId"),
        bundleId = config.getString("apns.bundleId"),
        keyId = config.getString("apns.keyId"),
        certificate = config.getString("apns.certificate"),
        sendingToProdServer = config.getBoolean("apns.sendingToProdServer"),
        dryRun = config.getBoolean("dryrun")
      )
    )
  }

  def fetchFirebase(): FcmWorkerConfiguration = {
    val config = fetchConfiguration()
    FcmWorkerConfiguration(
      jdbcConfig(config),
      FcmConfig(
        serviceAccountKey = config.getString("fcm.serviceAccountKey"),
        debug = config.getBoolean("fcm.debug"),
        dryRun = config.getBoolean("dryrun")
      )
    )
  }

}
