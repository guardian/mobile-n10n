package com.gu.notifications.worker.delivery.apns.models

case class ApnsConfig(
  teamId: String,
  bundleId: String,
  keyId: String,
  certificate: String,
  sendingToProdServer: Boolean = false,
  dryRun: Boolean = true
)
