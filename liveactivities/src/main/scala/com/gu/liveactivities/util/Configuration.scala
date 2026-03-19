package com.gu.liveactivities.util

import com.typesafe.config.Config
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region.EU_WEST_1

case class IosConfiguration(
  teamId: String,
  bundleId: String,
  keyId: String,
  certificate: Option[String],
  sendingToProdServer: Boolean = false,
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
    ConfigurationLoader.load(identity) {
      case AwsIdentity(_, _, stage, region) => SSMConfigurationLocation(s"/notifications/$stage/liveactivities/$platform", region)
    }
  }

  def fetchIos(): IosConfiguration = {
    val config = fetchConfiguration("ios")
    IosConfiguration(
      teamId = config.getString("apns.teamId"),
      bundleId = config.getString("apns.bundleId"),
      keyId = config.getString("apns.keyId"),
      certificate = if (config.hasPath("apns.certificate")) Some(config.getString("apns.certificate")) else None,
      sendingToProdServer = config.getBoolean("apns.sendingToProdServer")
    )
  }
}
