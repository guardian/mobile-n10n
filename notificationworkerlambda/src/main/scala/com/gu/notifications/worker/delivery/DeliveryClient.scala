package com.gu.notifications.worker.delivery

import models.Notification

import java.util.UUID
import java.util.concurrent.CompletableFuture

import scala.concurrent.ExecutionContextExecutor

trait DeliveryClient {

  type Success <: DeliverySuccess
  type Payload <: DeliveryPayload

  def sendNotification(notificationId: UUID, token: String, payload: Payload, dryRun: Boolean)(implicit ece: ExecutionContextExecutor): CompletableFuture[Either[DeliveryException, Success]]
  def payloadBuilder: Notification => Option[Payload]
  val dryRun: Boolean
}

