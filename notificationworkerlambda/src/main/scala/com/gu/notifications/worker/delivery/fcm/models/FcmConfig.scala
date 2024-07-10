package com.gu.notifications.worker.delivery.fcm.models

case class FcmConfig(
  serviceAccountKey: String,
  connectTimeout: Int,
  requestTimeout: Int,
  debug: Boolean = false,
  dryRun: Boolean = true,
)
