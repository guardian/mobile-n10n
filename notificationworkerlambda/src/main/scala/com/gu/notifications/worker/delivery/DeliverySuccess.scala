package com.gu.notifications.worker.delivery

sealed trait DeliverySuccess {
  def token: String
  def dryRun: Boolean
}

sealed trait BatchDeliverySuccess {
  def responses: List[Either[DeliveryException, DeliverySuccess]]
}
case class ApnsDeliverySuccess(token: String, dryRun: Boolean = false) extends DeliverySuccess
case class FcmDeliverySuccess(token: String, messageId: String, dryRun: Boolean = false) extends DeliverySuccess
case class FcmBatchDeliverySuccess(responses: List[Either[DeliveryException, FcmDeliverySuccess]], notificationId: String) extends BatchDeliverySuccess

case class ApnsBatchDeliverySuccess(responses: List[Either[DeliveryException, ApnsDeliverySuccess]], notificationId: String) extends BatchDeliverySuccess


