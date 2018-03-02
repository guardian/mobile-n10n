package utils

import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.{AppIdentity, AwsIdentity}
import play.api.ApplicationLoader.Context
import play.api._

abstract class CustomApplicationLoader extends ApplicationLoader {
  def buildComponents(context: Context): BuiltInComponents

  lazy val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    InstanceProfileCredentialsProvider.getInstance
  )

  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val identity = AppIdentity.whoAmI("notifications")
    val config = ConfigurationLoader.load(identity, credentials) {
      case AwsIdentity(app, stack, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/$stack")
    }
    val loadedConfig = Configuration(config)
    val newContext = context.copy(initialConfiguration = context.initialConfiguration ++ loadedConfig )
    buildComponents(newContext).application
  }
}
