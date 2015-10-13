package services

import javax.inject.Inject

import auditor.AuditorGroupConfig
import com.gu.conf.ConfigurationFactory

import scala.concurrent.ExecutionContext

case class NotificationHubConfiguration(
  endpointUri: String,
  hubName: String,
  sharedKeyName: String,
  sharedKeyValue: String
)

class Configuration @Inject()()(implicit executionContext: ExecutionContext) {

  private lazy val conf = ConfigurationFactory.getConfiguration(
    applicationName = "registration",
    webappConfDirectory = "gu-conf"
  )

  lazy val notificationHubConfiguration = NotificationHubConfiguration(
    endpointUri= conf.getStringProperty("gu.msnotifications.endpointUri").get,
    hubName = conf.getStringProperty("gu.msnotifications.hubname").get,
    sharedKeyName = conf.getStringProperty("gu.msnotifications.sharedKeyName").get,
    sharedKeyValue = conf.getStringProperty("gu.msnotifications.sharedKeyValue").get
  )

  lazy val auditorConfiguration = AuditorGroupConfig(
    hosts = Set(
      conf.getStringProperty("notifications.auditor.content-notifications").get,
      conf.getStringProperty("notifications.auditor.goal-alerts").get
    )
  )
}
