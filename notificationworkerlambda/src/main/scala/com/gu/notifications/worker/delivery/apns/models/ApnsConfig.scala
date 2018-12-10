package com.gu.notifications.worker.delivery.apns.models

case class ApnsConfig(
  teamId: String,
  bundleId: String,
  newsstandBundleId: String,
  keyId: String,
  certificate: String,
  sendingToProdServer: Boolean = false,
  dryRun: Boolean = true
)
