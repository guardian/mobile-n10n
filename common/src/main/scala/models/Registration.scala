package models

case class Registration(
  deviceToken: DeviceToken,
  platform: Platform,
  topics: Set[Topic],
  buildTier: Option[String]
)