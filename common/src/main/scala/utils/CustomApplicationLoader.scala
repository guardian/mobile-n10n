package utils

import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.{AppIdentity, AwsIdentity}
import play.api.ApplicationLoader.Context
import play.api._

import scala.util.control.NonFatal

abstract class CustomApplicationLoader extends ApplicationLoader {
  def buildComponents(context: Context): BuiltInComponents

  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val logger = Logger.apply(this.getClass)
    try {
      val identity = AppIdentity.whoAmI("notifications")
      val config = ConfigurationLoader.load(identity, new MobileAwsCredentialsProvider) {
        case AwsIdentity(_, stack, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/$stack")
      }
      val loadedConfig = Configuration(config)
      val newContext = context.copy(initialConfiguration = context.initialConfiguration ++ loadedConfig)
      buildComponents(newContext).application
    } catch {
      case NonFatal(e) =>
        logger.error("Failed to start the application", e)
        throw e
    }
  }
}
