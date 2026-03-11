package com.gu.liveactivities

object LambdaLocalRun extends App {

  println("Start running ChannelManagerLambda locally")
  ChannelManagerLambda.handleRequest()
  println("Finished running ChannelManagerLambda locally")
}
