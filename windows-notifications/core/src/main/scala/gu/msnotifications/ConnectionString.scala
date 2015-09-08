package gu.msnotifications

import org.scalactic._

/**
 * Response Codes @ [[https://msdn.microsoft.com/en-us/library/azure/dn223265.aspx]]
 */

case class ConnectionString(value: String) {
  def buildNotificationHub(notificationHubName: String): NotificationHub Or Every[ErrorMessage] = {
    val regexMatch = """Endpoint=sb:\/\/(([^\.]+)-ns).servicebus.windows.net\/\;SharedAccessKeyName=([^;]+);SharedAccessKey=(.*)""".r

    value match {
       case regexMatch(namespace, _, keyName, key) =>
        Good {
          NotificationHub(
            namespace = namespace,
            notificationHub = notificationHubName,
            secretKeyName = keyName,
            secretKey = key
          )
        }
      case _ =>
        Bad(One("Endpoint does not match expected type of string."))
    }
  }
}
