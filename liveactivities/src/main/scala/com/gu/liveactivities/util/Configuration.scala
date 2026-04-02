package com.gu.liveactivities.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

case class IosConfiguration(
  teamId: String,
  bundleId: String,
  keyId: String,
  certificate: String,
  sendingToProdServer: Boolean = false,
  stage: String,
  region: String
)

object Configuration extends Logging {

  private def fetchConfiguration(platform: String): Config = {
    val defaultAppName = s"liveactivities-$platform"
    val identity = Option(System.getenv("MOBILE_LOCAL_DEV")) match {
      case Some(_) => DevIdentity(defaultAppName)
      case None =>
        AppIdentity
          .whoAmI(defaultAppName, DefaultCredentialsProvider.builder().build())
          .getOrElse(DevIdentity(defaultAppName))
    }
    logger.info(s"Fetching configuration for ${identity}")
    val config = ConfigurationLoader.load(identity) {
      case AwsIdentity(_, _, stage, region) => SSMConfigurationLocation(s"/notifications/$stage/liveactivities/$platform", region)
    }
    identity match {
      case AwsIdentity(_, _, stage, region) =>
        config
          .withValue("stage", ConfigValueFactory.fromAnyRef(stage))
          .withValue("region", ConfigValueFactory.fromAnyRef(region))
      case DevIdentity(_) =>
        config
          .withValue("stage", ConfigValueFactory.fromAnyRef("DEV"))
          .withValue("region", ConfigValueFactory.fromAnyRef("eu-west-1"))
    }
  }

  def fetchIos(): IosConfiguration = {
    val config = fetchConfiguration("ios")
    IosConfiguration(
      teamId = config.getString("apns.teamId"),
      bundleId = config.getString("apns.bundleId"),
      keyId = config.getString("apns.keyId"),
      certificate = config.getString("apns.certificate"),
      sendingToProdServer = config.getBoolean("apns.sendingToProdServer"),
      stage = config.getString("stage"),
      region = config.getString("region")
    )
  }
}
