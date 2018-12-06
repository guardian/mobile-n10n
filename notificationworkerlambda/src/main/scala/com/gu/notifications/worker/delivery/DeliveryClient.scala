package com.gu.notifications.worker.delivery

import java.util.UUID

import models.{Notification, Platform}

import scala.concurrent.ExecutionContextExecutor

trait DeliveryClient {

  type Success <: DeliverySuccess
  type Payload <: DeliveryPayload

  def close(): Unit
  def sendNotification(notificationId: UUID, token: String, payload: Payload, platform: Platform)
    (onComplete: Either[Throwable, Success] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit
  def payloadBuilder: Notification => Option[Payload]
}

