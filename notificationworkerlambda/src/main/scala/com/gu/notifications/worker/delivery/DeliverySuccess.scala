package com.gu.notifications.worker.delivery

sealed trait DeliverySuccess {
  def token: String
  def dryRun: Boolean
}

sealed trait BatchDeliverySuccess {
  def tokens: List[String]
  def dryRun: Boolean
}
case class ApnsDeliverySuccess(token: String, dryRun: Boolean = false) extends DeliverySuccess
case class FcmDeliverySuccess(token: String, messageId: String, dryRun: Boolean = false) extends DeliverySuccess
case class FcmBatchDeliverySuccess(tokens: List[String], messageId: String, dryRun: Boolean = false) extends BatchDeliverySuccess
