package services

import conf.NotificationConfiguration

class Configuration extends NotificationConfiguration("report") {
  lazy val apiKey = conf.getStringProperty("notifications.api.secretKey")
}
