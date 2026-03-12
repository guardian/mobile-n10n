package com.gu.liveactivities

object ChannelManagerLambda {


  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def handleRequest(): Unit = {
    val client = new ChannelApiClient()
    val channelFuture = client.createChannel()
    channelFuture.onComplete {
      case scala.util.Success(channelId) => println(s"Channel created successfully with id: $channelId")
      case scala.util.Failure(exception) => println(s"Failed to create channel: ${exception.getMessage}")
    }
     // Keep the main thread alive to allow the async operation to complete
    Thread.sleep(5000)
  }
}

