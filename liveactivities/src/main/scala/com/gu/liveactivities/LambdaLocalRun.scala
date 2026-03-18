package com.gu.liveactivities

object LambdaLocalRun extends App {

  println("Start running ChannelManagerLambda locally")
  val request = ChannelRequest("match123", "channel123", toCreate = true)
  ChannelManagerLambda.handleRequest(request)
  println("Finished running ChannelManagerLambda locally")
}
