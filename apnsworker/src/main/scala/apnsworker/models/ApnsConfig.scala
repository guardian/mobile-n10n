package apnsworker.models

case class ApnsConfig(
  teamId: String,
  bundleId: String,
  keyId: String,
  certificate: String,
  sendingToProdServer: Boolean = false
)
