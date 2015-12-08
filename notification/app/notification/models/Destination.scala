package notification.models

import models.{UserId, Topic}

object Destination {
  type Destination = Either[Set[Topic], UserId]

  def apply(userId: UserId): Destination = Right(userId)
  def apply(topic: Topic): Destination = Left(Set(topic))
  def apply(topics: Set[Topic]): Destination = Left(topics)
}
