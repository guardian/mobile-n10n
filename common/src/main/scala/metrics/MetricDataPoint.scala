package metrics

import software.amazon.awssdk.services.cloudwatch.model.StandardUnit

case class MetricDataPoint(
  namespace: String = "Notifications",
  name: String,
  value: Double,
  unit: StandardUnit = StandardUnit.NONE
)
