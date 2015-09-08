package gu.msnotifications

import java.net.URI
import org.scalactic._

/**
 * Response Codes @ [[https://msdn.microsoft.com/en-us/library/azure/dn223265.aspx]]
 */

case class ConnectionString(connectionString: String) {

  private case class Endpoint(uri: URI) {
    def namespaceO = {
      val hostMatch = s"""(.*)\\.servicebus\\.windows\\.net""".r
      PartialFunction.condOpt(uri.getHost) {
        case hostMatch(host) => host
      }
    }

    def queryParameters = {
      val queryParametersMatch = s"""([^=]+)=(.*)""".r
      uri.getPath.split(";").collect {
        case queryParametersMatch(key, value) => key -> value
      }.toMap
    }
  }

  private def endpointO = {
    val regexMatch = """Endpoint=(.*)""".r
    PartialFunction.condOpt(connectionString) {
      case regexMatch(uri) => Endpoint(new URI(uri))
    }
  }

  def buildNotificationHub(notificationHubName: String): NotificationHub Or Every[ErrorMessage] = {
    {
      for {
        endpoint <- endpointO
        namespace <- endpoint.namespaceO
        queryParameters = endpoint.queryParameters
        keyName <- queryParameters.get("SharedAccessKeyName")
        key <- queryParameters.get("SharedAccessKey")
      } yield NotificationHub(
        namespace = namespace,
        notificationHub = notificationHubName,
        secretKeyName = keyName,
        secretKey = key
      )
    } match {
      case Some(notificationHub) => Good(notificationHub)
      case None => Bad(One("Unable to parse the connection string to build a notification hub. Check the tests for the right format."))
    }
  }
}
