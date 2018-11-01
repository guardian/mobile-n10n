package com.gu.notifications.worker.delivery

import java.util.UUID

import models.{Notification, Platform}

import scala.concurrent.ExecutionContextExecutor

trait DeliveryClient[P <: DeliveryPayload, S <: DeliverySuccess] {
  def platform: Platform
  def close(): Unit
  def sendNotification(notificationId: UUID, token: String, payload: P)
    (onComplete: Either[Throwable, S] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit
  def payloadBuilder: Notification => Option[P]
}

