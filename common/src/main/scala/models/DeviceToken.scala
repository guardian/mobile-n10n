package models

sealed trait DeviceToken {
  def fcmToken: String
  def azureToken: String
}

case class FcmToken(fcmToken: String) extends DeviceToken {
  override def azureToken: String = throw new RuntimeException("No azure token for this device token")
}
case class AzureToken(azureToken: String) extends DeviceToken {
  override def fcmToken: String = throw new RuntimeException("No fcm token for this device token")
}
case class BothTokens(azureToken: String, fcmToken: String) extends DeviceToken