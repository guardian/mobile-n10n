package gu.msnotifications

case class NotificationHubConnection(notificationsHubUrl: String, secretKeyName: String, secretKey: String) {

  def authorizationHeader(uri: String) = SasTokenGeneration.generateSasToken(
    sasKeyName = secretKeyName,
    sasKeyValue = secretKey,
    uri = uri
  )

}