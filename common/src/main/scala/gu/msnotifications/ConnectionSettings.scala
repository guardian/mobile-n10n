package gu.msnotifications

import java.net.URI

case class Endpoint(uri: URI)
object Endpoint {
  def parse(uri: String) = Endpoint(new URI(uri))
}

case class ConnectionSettings(endpoint: Endpoint, keyName: String, key: String) {
  def buildHubConnection(hubName: String) = NotificationHubConnection(
    notificationsHubUrl = s"${endpoint.uri}/$hubName",
    secretKeyName = keyName,
    secretKey = key
  )
}
