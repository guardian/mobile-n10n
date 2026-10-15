package utils

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import play.api.ApplicationLoader.Context
import play.api._
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain => AwsCredentialsProviderChainV2, DefaultCredentialsProvider => DefaultCredentialsProviderV2, ProfileCredentialsProvider => ProfileCredentialsProviderV2}
import software.amazon.awssdk.regions.Region.EU_WEST_1

abstract class CustomApplicationLoader extends ApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents

  lazy val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    InstanceProfileCredentialsProvider.getInstance
  )

  lazy val credentialsv2 = AwsCredentialsProviderChainV2.of(
    ProfileCredentialsProviderV2.builder.profileName("mobile").build,
    DefaultCredentialsProviderV2.builder().build()
  )

  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val defaultAppName = "notifications"
    val identity = Option(System.getenv("MOBILE_LOCAL_DEV")) match {
      case Some(_) => DevIdentity(defaultAppName)
      case None =>
        AppIdentity
          .whoAmI(defaultAppName, credentialsv2)
          .getOrElse(DevIdentity(defaultAppName))
    }
    val config = ConfigurationLoader.load(identity, credentialsv2) {
      case AwsIdentity(app, stack, stage, region) => SSMConfigurationLocation(s"/notifications/$stage/$stack", region)
    }
    val loadedConfig = Configuration(config)
    val newContext = context.copy(initialConfiguration = loadedConfig.withFallback(context.initialConfiguration))
    buildComponents(identity, newContext).application
  }
}
