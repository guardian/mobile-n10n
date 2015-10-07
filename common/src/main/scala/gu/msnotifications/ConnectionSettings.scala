package gu.msnotifications

import java.net.URI

import gu.msnotifications.HubFailure.HubInvalidConnectionString

import scalaz.\/

import scalaz.std.option.optionSyntax._

/**
 * Response Codes @ [[https://msdn.microsoft.com/en-us/library/azure/dn223265.aspx]]
 */

case class Endpoint(uri: URI) {
  def queryParameters = {
    val queryParametersMatch = s"""([^=]+)=(.*)""".r
    uri.getPath.split(";").collect {
      case queryParametersMatch(key, value) => key -> value
    }.toMap
  }
}

object ConnectionSettings {
  private def endpointO(connectionString: String) = {
    val regexMatch = """Endpoint=(.*)""".r
    PartialFunction.condOpt(connectionString) {
      case regexMatch(uri) => Endpoint(new URI(uri))
    }
  }

  def fromString(s: String): \/[HubFailure, ConnectionSettings] = {
    for {
      endpoint <- endpointO(s) \/> HubInvalidConnectionString("Endpoint must start with \"Endpoint=\"")
      queryParameters = endpoint.queryParameters
      keyName <- queryParameters.get("SharedAccessKeyName") \/> HubInvalidConnectionString("Missing parameter SharedAccessKeyName")
      key <- queryParameters.get("SharedAccessKey") \/> HubInvalidConnectionString("Missing parameter SharedAccessKey")
    } yield ConnectionSettings(endpoint, keyName, key)
  }
}

case class ConnectionSettings(endpoint: Endpoint, keyName: String, key: String) {
  def buildNotificationHub(notificationHubName: String) = NotificationHubConnection(
    notificationsHubUrl = s"https://${endpoint.uri.getHost}/$notificationHubName",
    secretKeyName = keyName,
    secretKey = key
  )
}
