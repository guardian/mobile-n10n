package gu.msnotifications

case class NotificationHubConnection(
  endpoint: String,
  sharedAccessKeyName: String,
  sharedAccessKey: String
) {
  def authorizationHeader(uri: String) = SasTokenGeneration.generateSasToken(
    sasKeyName = sharedAccessKeyName,
    sasKeyValue = sharedAccessKey,
    uri = uri
  )
}