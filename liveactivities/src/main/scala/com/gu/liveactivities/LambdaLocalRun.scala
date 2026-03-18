package com.gu.liveactivities

object LambdaLocalRun extends App {

  println("Start running ChannelManagerLambda locally")
  val createRequest = ChannelRequest("match123", "channel123", toCreate = true)
  val channelId = ChannelManagerLambda.handleRequest(createRequest, null)

  val closeRequest = ChannelRequest("match123", channelId, toCreate = false)
  ChannelManagerLambda.handleRequest(closeRequest, null)  
  println("Finished running ChannelManagerLambda locally")
}
