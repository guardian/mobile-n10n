package com.gu.notifications.worker.delivery

sealed trait DeliverySuccess {
  def token: String
}
case class ApnsDeliverySuccess(token: String) extends DeliverySuccess
case class FcmDeliverySuccess(token: String, messageId: String) extends DeliverySuccess
