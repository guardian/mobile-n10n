package utils

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import play.api.ApplicationLoader.Context
import play.api._
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain => AwsCredentialsProviderChainV2, DefaultCredentialsProvider => DefaultCredentialsProviderV2, ProfileCredentialsProvider => ProfileCredentialsProviderV2}

abstract class CustomApplicationLoader extends ApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents

  lazy val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    InstanceProfileCredentialsProvider.getInstance
  )

  lazy val credentialsv2 = AwsCredentialsProviderChainV2.of(
    ProfileCredentialsProviderV2.builder.profileName("mobile").build,
    DefaultCredentialsProviderV2.create
  )

  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val identity = AppIdentity.whoAmI("notifications")
    val config = ConfigurationLoader.load(identity, credentialsv2) {
      case AwsIdentity(app, stack, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/$stack")
    }
    val loadedConfig = Configuration(config)
    val newContext = context.copy(initialConfiguration = loadedConfig.withFallback(context.initialConfiguration))
    buildComponents(identity, newContext).application
  }
}
