package com.gu.mobile.notifications.football

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.{AppIdentity, AwsIdentity}
import com.typesafe.config.Config
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain => AwsCredentialsProviderChainV2,
  ProfileCredentialsProvider => ProfileCredentialsProviderV2,
  DefaultCredentialsProvider => DefaultCredentialsProviderV2
}

class Configuration extends Logging {

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance()
  )

  val credentialsv2 = AwsCredentialsProviderChainV2.of(
     ProfileCredentialsProviderV2.builder.profileName("mobile").build,
    DefaultCredentialsProviderV2.create
  )

  val appName = Option(System.getenv("App")).getOrElse(sys.error("No app name set. Lambda will not run"))
  val stage = Option(System.getenv("Stage")).getOrElse(sys.error("No app name set. Lambda will not run"))

  private val conf: Config = {
     val identity = AppIdentity.whoAmI(defaultAppName = appName)
     logger.info(s"Tryling: ${identity}")
     ConfigurationLoader.load(identity = identity, credentials = credentialsv2) {
       case AwsIdentity(app, stack, stage, _) =>
         val path = s"/$app/$stage/$stack"
         logger.info(s"Attempting to retrieve config from: $path")
         SSMConfigurationLocation(path = path)
     }
  }

  val paApiKey = conf.getString("pa.api-key")
  val paHost = conf.getString("pa.host")
  val notificationsHost = conf.getString("notifications-client.host")
  val notificationsApiKey = conf.getString("notifications-client.api-key")
  val mapiHost = conf.getString("mapi.host")
  val capiApiKey = conf.getString("capi.key")
}
