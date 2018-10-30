package com.gu.notifications.worker

import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

case class Configuration(
  databasePassword: String
)

object Configuration {
  def fetch(): Configuration = {
    val identity = AppIdentity.whoAmI(defaultAppName = "notification-worker")
    val config = ConfigurationLoader.load(identity) {
      case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/workers")
    }

    Configuration(
      config.getString("registration.db.password")
    )
  }
}
