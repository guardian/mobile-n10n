package com.gu.notifications.worker.delivery.apns

import java.util.{Timer, TimerTask}

import com.turo.pushy.apns.PushNotificationResponse
import com.turo.pushy.apns.util.concurrent.{PushNotificationFuture, PushNotificationResponseListener}

/**
  * This is an attempt to confirm the hypothesis that sometimes notifications are sent to APNs but dropped on their side.
  * It would mean pushy would never complete its future, leaving our code hanging forever.
  * The primary goal of this class is to help us confirm if the hypothesis is correct.
  */
trait PushNotificationResponseListenerWithTimeout[A <: com.turo.pushy.apns.ApnsPushNotification] extends PushNotificationResponseListener[A] {

  def timeout(): Unit
  def operationCompleteWithoutTimeout(future: PushNotificationFuture[A, PushNotificationResponse[A]]): Unit

  private val task = new TimerTask {
    override def run(): Unit = {
      timeout()
    }
  }

  def startTimeout(timer: Timer, timeoutInMs: Long): Unit = {
    timer.schedule(task, timeoutInMs)
  }

  override def operationComplete(future: PushNotificationFuture[A, PushNotificationResponse[A]]): Unit = {
    task.cancel()
    operationCompleteWithoutTimeout(future)
  }
}
