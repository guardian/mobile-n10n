package notification.models

import models.Topic

object Destination {
  type Destination = Set[Topic]


  def apply(topic: Topic): Destination = Set(topic)
  def apply(topics: Set[Topic]): Destination = topics
}
