package gu.msnotifications

case class NotificationHubConnection(notificationsHubUrl: String, sharedKeyName: String, sharedKeyValue: String) {

  def authorizationHeader(uri: String): String = SasTokenGeneration.generateSasToken(
    sasKeyName = sharedKeyName,
    sasKeyValue = sharedKeyValue,
    uri = uri
  )

}
