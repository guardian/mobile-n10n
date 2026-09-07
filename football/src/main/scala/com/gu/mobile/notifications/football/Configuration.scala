package com.gu.mobile.notifications.football

import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.typesafe.config.Config
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain => AwsCredentialsProviderChainV2, DefaultCredentialsProvider => DefaultCredentialsProviderV2, ProfileCredentialsProvider => ProfileCredentialsProviderV2}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

class Configuration extends Logging {

  val credentials = AwsCredentialsProviderChainV2.of(
    ProfileCredentialsProviderV2.builder.profileName("mobile").build,
    DefaultCredentialsProviderV2.builder.build()
  )

  val appName = Option(System.getenv("App")).getOrElse(sys.error("No app name set. Lambda will not run"))
  val stage = Option(System.getenv("Stage")).getOrElse(sys.error("No app name set. Lambda will not run"))

  private val conf: Config = {
    val identity = Option(System.getenv("MOBILE_LOCAL_DEV")) match {
      case Some(_) => DevIdentity(appName)
      case None =>
        AppIdentity
          .whoAmI(defaultAppName = appName, credentials)
          .getOrElse(DevIdentity(appName))
    }

    logger.info(s"Detected AppIdentity: $identity")
    ConfigurationLoader.load(identity = identity, credentials = credentials) {
      case AwsIdentity(app, stack, stage, region) =>
        val path = s"/$app/$stage/$stack"
        logger.info(s"Attempting to retrieve config from: $path")
        SSMConfigurationLocation(path = path, region)
    }
  }

  val paApiKey = conf.getString("pa.api-key")
  val paHost = conf.getString("pa.host")
  val notificationsHost = conf.getString("notifications-client.host")
  val notificationsApiKey = conf.getString("notifications-client.api-key")
  val mapiHost = conf.getString("mapi.host")
  val capiApiKey = conf.getString("capi.key")
}
