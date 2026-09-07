package utils

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import play.api.ApplicationLoader.Context
import play.api._


abstract class CustomApplicationLoader extends ApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents

  lazy val credentials: AwsCredentialsProvider = MobileAwsCredentialsProvider.mobileAwsCredentialsProvider

  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val defaultAppName = "notifications"
    val identity = Option(System.getenv("MOBILE_LOCAL_DEV")) match {
      case Some(_) => DevIdentity(defaultAppName)
      case None =>
        AppIdentity
          .whoAmI(defaultAppName, credentials)
          .getOrElse(DevIdentity(defaultAppName))
    }
    val config = ConfigurationLoader.load(identity, credentials) {
      case AwsIdentity(app, stack, stage, region) => SSMConfigurationLocation(s"/notifications/$stage/$stack", region)
    }
    val loadedConfig = Configuration(config)
    val newContext = context.copy(initialConfiguration = loadedConfig.withFallback(context.initialConfiguration))
    buildComponents(identity, newContext).application
  }
}
