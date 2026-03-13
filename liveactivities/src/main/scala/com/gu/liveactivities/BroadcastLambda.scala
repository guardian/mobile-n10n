package com.gu.liveactivities

object BroadcastLambda {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def handleRequest(): Unit = {
    val client = new BroadcastApiClient()
    val broadcastFuture = client.sendToChannel("test-channel-id", None, None)
    broadcastFuture.onComplete {
      case scala.util.Success(apnResponse) => println(s"Sent broadcast successfully with Response: $apnResponse")
      case scala.util.Failure(exception) => println(s"Failed to send broadcast: ${exception.getMessage}")
    }
    // Keep the main thread alive to allow the async operation to complete
    Thread.sleep(5000)
  }
}
