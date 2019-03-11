package com.gu.notifications.worker

object TopicCounterLocalRun extends App {
  new TopicCounterLambda().runLocally()
}
