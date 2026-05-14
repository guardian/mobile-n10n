package com.gu.notifications.worker.delivery

import java.time.Instant

sealed trait DeliverySuccess {
  def token: String
  def dryRun: Boolean

  val deliveryTime: Instant
}

case class ApnsDeliverySuccess(token: String, deliveryTime: Instant, dryRun: Boolean = false) extends DeliverySuccess
case class FcmDeliverySuccess(token: String, messageId: String, deliveryTime: Instant, dryRun: Boolean = false) extends DeliverySuccess
