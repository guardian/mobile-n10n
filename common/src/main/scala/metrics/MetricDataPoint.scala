package metrics

import com.amazonaws.services.cloudwatch.model.StandardUnit

case class MetricDataPoint(
  namespace: String = "Notifications",
  name: String,
  value: Double,
  unit: StandardUnit = StandardUnit.None
)
