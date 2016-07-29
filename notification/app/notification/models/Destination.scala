package notification.models

import models.{UniqueDeviceIdentifier, Topic}

object Destination {
  type Destination = Either[Set[Topic], UniqueDeviceIdentifier]

  def apply(userId: UniqueDeviceIdentifier): Destination = Right(userId)
  def apply(topic: Topic): Destination = Left(Set(topic))
  def apply(topics: Set[Topic]): Destination = Left(topics)
}
