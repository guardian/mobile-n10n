package com.gu.notifications.worker.delivery.fcm.models

case class FcmConfig(
  serviceAccountKey: String,
  debug: Boolean = false,
  dryRun: Boolean = true
)
