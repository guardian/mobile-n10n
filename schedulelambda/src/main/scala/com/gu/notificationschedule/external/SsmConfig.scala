package com.gu.notificationschedule.external

import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.util.Try

case class SsmConfig(
                      app: String,
                      stack: String,
                      stage: String,
                      config: Config) {
}

object SsmConfigLoader {

  def load(awsIdentitySupplier: () => AppIdentity = () => AppIdentity.whoAmI(defaultAppName = "schedule")): SsmConfig = {
    Try {
      val identity: AppIdentity = awsIdentitySupplier()
      val config: Config = ConfigurationLoader.load(identity) {
        case identity: AwsIdentity => SSMConfigurationLocation(s"/notifications/${identity.stage}/${identity.stack}")
      }
      identity match {
        case awsIdentity: AwsIdentity => SsmConfig(awsIdentity.app, awsIdentity.stack, awsIdentity.stage, config)
        case _ => {
          val notAnAppMessage: String = "Not running an app"
          LoggerFactory.getLogger("SsmConfigLoader").error(notAnAppMessage)
          throw new IllegalStateException(notAnAppMessage)
        }
      }
    }.fold(t => {
      LoggerFactory.getLogger("SsmConfigLoader").error("Failed to load config", t)
      throw t
    }, x => x)

  }
}
