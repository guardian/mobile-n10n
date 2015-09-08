package services

import javax.inject.Inject

import gu.msnotifications.{ConnectionString, NotificationHub}
import play.api.Configuration
import org.scalactic._
import org.scalactic.Accumulation._

final class ApiConfiguration @Inject()(configuration: Configuration) {

  private def getConfigurationProperty(name: String): String Or One[ErrorMessage] = {
    configuration.getString(name) match {
      case Some(value) => Good(value)
      case None => Bad(One(s"Could not find property $name"))
    }
  }

  def notificationHubOR = {
    withGood(
      getConfigurationProperty("gu.msnotifications.connectionstring").map(ConnectionString.apply),
      getConfigurationProperty("gu.msnotifications.hubname")
    ) { (connectionString, hubName) => connectionString.buildNotificationHub(hubName) }
  }.flatMap(identity) // scalactic is missing .flatten?

  def notificationHub = notificationHubOR.get

}
