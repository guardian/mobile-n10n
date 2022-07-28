package com.gu.notifications.workerlambda.delivery.fcm.models

case class FcmConfig(
  serviceAccountKey: String,
  debug: Boolean = false,
  dryRun: Boolean = true
)
