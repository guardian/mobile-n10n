package com.gu.notifications.worker.delivery

import java.util.UUID

import models.Notification

import scala.concurrent.ExecutionContextExecutor

trait DeliveryClient[P <: DeliveryPayload, S <: DeliverySuccess] {
  def close(): Unit
  def sendNotification(notificationId: UUID, token: String, payload: P)
    (onComplete: Either[Throwable, S] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit
  def payloadBuilder: Notification => Option[P]
}

