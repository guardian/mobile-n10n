package models

sealed trait DeviceToken {
  def fcmToken: String
  def azureToken: String
  def hasFcmToken: Boolean
  def hasAzureToken: Boolean
}

case class FcmToken(fcmToken: String) extends DeviceToken {
  override def azureToken: String = throw new RuntimeException("No azure token for this device token")
  override def hasFcmToken: Boolean = true
  override def hasAzureToken: Boolean = false
}
case class AzureToken(azureToken: String) extends DeviceToken {
  override def fcmToken: String = throw new RuntimeException("No fcm token for this device token")
  override def hasFcmToken: Boolean = false
  override def hasAzureToken: Boolean = true
}
case class BothTokens(azureToken: String, fcmToken: String) extends DeviceToken {
  override def hasFcmToken: Boolean = true
  override def hasAzureToken: Boolean = true
}