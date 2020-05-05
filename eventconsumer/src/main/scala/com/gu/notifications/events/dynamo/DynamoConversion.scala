package com.gu.notifications.events.dynamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.notifications.events.model.EventAggregation

import scala.jdk.CollectionConverters._

object DynamoConversion {

  def toAttributeValue(eventAggregation: EventAggregation): AttributeValue = {
    val platform = eventAggregation.platformCounts
    new AttributeValue().withM(Map(
      "platform" -> new AttributeValue().withM(Map(
        "total" -> new AttributeValue().withN(platform.total.toString),
        "ios" -> new AttributeValue().withN(platform.ios.toString),
        "android" -> new AttributeValue().withN(platform.android.toString)
      ).asJava)
    ).asJava)
  }
}
