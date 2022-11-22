package com.gu.notifications.worker.delivery

import java.util.UUID

import models.Notification

import scala.concurrent.ExecutionContextExecutor

trait DeliveryClient {

  type Success <: DeliverySuccess
  type Payload <: DeliveryPayload
  type BatchSuccess <: BatchDeliverySuccess

  def close(): Unit
  def sendNotification(notificationId: UUID, token: String, payload: Payload, dryRun: Boolean)
    (onComplete: Either[DeliveryException, Success] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit
  def sendBatchNotification(notificationId: UUID, token: List[String], payload: Payload, dryRun: Boolean)
    (onComplete: Either[DeliveryException, BatchSuccess] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit
  def payloadBuilder: Notification => Option[Payload]
  val dryRun: Boolean
}

