package com.gu.notifications.events.dynamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.notifications.events.model.{EventAggregation, PlatformCount}

import scala.collection.JavaConverters._

object DynamoConversion {

  def fromAttributeValue(eventAggregationAv: AttributeValue): EventAggregation = {
    val eventMap = eventAggregationAv.getM.asScala
    EventAggregation(
      platformCounts = platformFromAttributeValue(eventMap.get("platform"))
    )
  }

  private def platformFromAttributeValue(platformAv: Option[AttributeValue]): PlatformCount = platformAv match {
    case None => PlatformCount.empty
    case Some(platformCount) =>
      val platformMap = platformCount.getM.asScala
      PlatformCount(
        total = platformMap("total").getN.toInt,
        ios = platformMap("ios").getN.toInt,
        android = platformMap("android").getN.toInt
      )
  }

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
