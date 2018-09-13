package com.gu.notifications.events

import java.time.{Duration, LocalDateTime}

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.notifications.events.model.{EventAggregation, PlatformCount, ProviderCount}

import scala.collection.JavaConverters._

object DynamoConversion {
  implicit val localDateOrdering: Ordering[LocalDateTime] = _ compareTo _

  def fromAttributeValue(eventAggregationAv: AttributeValue, notificationid: String, sentTime: LocalDateTime): EventAggregation = {
    val eventMap = eventAggregationAv.getM.asScala
    val providerMap = eventMap("provider").getM.asScala
    EventAggregation(
      platformCounts = platformFromAttributeValue(eventMap("platform")),
      providerCounts = ProviderCount(providerMap("total").getN.toInt, platformFromAttributeValue(providerMap("azure")), platformFromAttributeValue(providerMap("firebase"))),
      timing = eventMap("timing").getL.asScala.toList.map(av => {
        val list = av.getL.asScala.toList
        (list(0).getN.toInt, list(1).getN.toInt)
      }).foldLeft((sentTime, Seq[(LocalDateTime, Int)]())) {
        case ((lastTime, newList), (offset, count)) => {
          val nextTime = lastTime.plusSeconds(offset * 10)
          (nextTime, newList :+ (nextTime, count))
        }
      }._2.toMap

    )
  }

  private def platformFromAttributeValue(platformAv: AttributeValue): PlatformCount = {
    val platformMap = platformAv.getM.asScala
    PlatformCount(
      total = platformMap("total").getN.toInt,
      ios = platformMap("ios").getN.toInt,
      android = platformMap("android").getN.toInt)
  }

  def toAttributeValue(eventAggregation: EventAggregation, sent: LocalDateTime): AttributeValue = {
    val platform = eventAggregation.platformCounts
    val provider = eventAggregation.providerCounts
    new AttributeValue().withM(Map(
      "platform" -> new AttributeValue().withM(Map(
        "total" -> new AttributeValue().withN(platform.total.toString),
        "ios" -> new AttributeValue().withN(platform.ios.toString),
        "android" -> new AttributeValue().withN(platform.android.toString)
      ).asJava),
      "provider" -> new AttributeValue().withM(Map(
        "total" -> new AttributeValue().withN(provider.total.toString),
        "azure" -> toAttributeValue(provider.azure),
        "firebase" -> toAttributeValue(provider.firebase)
      ).asJava),
      "timing" -> new AttributeValue().withL(
        eventAggregation.timing.toList
          .sortBy(_._1)
          .foldLeft((sent, Seq[(Int, Int)]())) {
            case ((lastDateTime, newList), (currentDateTime, count)) =>
              (currentDateTime, newList :+ (Duration.between(lastDateTime, currentDateTime).getSeconds.toInt / 10, count))
          }._2
          .map {
            case (offset, count) => new AttributeValue().withL(new AttributeValue().withN(offset.toString), new AttributeValue().withN(count.toString))
          }.asJava)
    ).asJava)
  }

  def toAttributeValue(platform: PlatformCount): AttributeValue = new AttributeValue().withM(Map(
    "total" -> new AttributeValue().withN(platform.total.toString),
    "ios" -> new AttributeValue().withN(platform.ios.toString),
    "android" -> new AttributeValue().withN(platform.android.toString)
  ).asJava)

}