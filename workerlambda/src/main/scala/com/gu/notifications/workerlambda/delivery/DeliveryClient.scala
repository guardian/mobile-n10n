package com.gu.notifications.workerlambda.delivery

import java.util.UUID

import com.gu.notifications.workerlambda.models.Notification

import scala.concurrent.ExecutionContextExecutor

trait DeliveryClient {

  type Success <: DeliverySuccess
  type Payload <: DeliveryPayload

  def close(): Unit
  def sendNotification(notificationId: UUID, token: String, payload: Payload, dryRun: Boolean)
    (onComplete: Either[Throwable, Success] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit
  def payloadBuilder: Notification => Option[Payload]
  val dryRun: Boolean
}

