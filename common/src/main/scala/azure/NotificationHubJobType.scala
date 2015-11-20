package azure

object NotificationHubJobType extends Enumeration {
  type NotificationHubJobType = Value
  val ExportRegistrations = Value
  val ImportCreateRegistrations = Value
  val ImportUpdateRegistrations = Value
  val ImportDeleteRegistrations = Value
}
