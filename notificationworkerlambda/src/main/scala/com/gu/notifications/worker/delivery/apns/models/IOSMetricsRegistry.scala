package com.gu.notifications.worker.delivery.apns.models

import com.codahale.metrics.{Counter, MetricRegistry}
import com.turo.pushy.apns.{ApnsClient, ApnsClientMetricsListener}

class IOSMetricsRegistry extends ApnsClientMetricsListener {
  val registry = new MetricRegistry()

  val writeFailures: Counter = registry.counter("pushy.writeFailures")
  val notificationSent: Counter = registry.counter("pushy.notificationSent")
  val notificationAccepted: Counter = registry.counter("pushy.notificationAccepted")
  val notificationRejected: Counter = registry.counter("pushy.notificationRejected")
  val connectionAdded: Counter = registry.counter("pushy.connectionAdded")
  val connectionRemoved: Counter = registry.counter("pushy.connectionRemoved")
  val connectionCreationFailed: Counter = registry.counter("pushy.connectionCreationFailed")

  override def handleWriteFailure(apnsClient: ApnsClient, notificationId: Long): Unit = writeFailures.inc()

  override def handleNotificationSent(apnsClient: ApnsClient, notificationId: Long): Unit = notificationSent.inc()

  override def handleNotificationAccepted(apnsClient: ApnsClient, notificationId: Long): Unit = notificationAccepted.inc()

  override def handleNotificationRejected(apnsClient: ApnsClient, notificationId: Long): Unit = notificationRejected.inc()

  override def handleConnectionAdded(apnsClient: ApnsClient): Unit = connectionAdded.inc()

  override def handleConnectionRemoved(apnsClient: ApnsClient): Unit = connectionRemoved.inc()

  override def handleConnectionCreationFailed(apnsClient: ApnsClient): Unit = connectionCreationFailed.inc()

}
