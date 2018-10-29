package fcmworker.models

case class FcmConfig(
  serviceAccountKey: String,
  debug: Boolean = false,
  dryRun: Boolean = true
)
