package services

import conf.NotificationConfiguration

final class Configuration extends NotificationConfiguration("notification") {
  lazy val hubEndpoint = conf.getStringProperty("gu.msnotifications.endpointUri").get
  lazy val hubSecretKeyName = conf.getStringProperty("gu.msnotifications.sharedKeyName").get
  lazy val hubSecretKey = conf.getStringProperty("gu.msnotifications.sharedKeyValue").get

  lazy val apiKey = conf.getStringProperty("gu.msnotifications.admin-api-key")
}
