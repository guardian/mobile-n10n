package com.gu.liveactivities

object ChannelManagerLambda {


  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val channelApiClient = new ChannelApiClient()
  val broadcastApiClient = new BroadcastApiClient()

  def onChannelCreated(channelId: String): Unit = {
    println(s"Message sent to channel with ID: $channelId")
    // You can add additional logic here to handle the sent message, such as logging or sending a notification.

    broadcastApiClient.sendToChannel(channelId, None, None).onComplete {
      case scala.util.Success(messageId) => {
        println(s"Message sent successfully with message ID $messageId")
        onMessageSent(channelId)
      }
      case scala.util.Failure(exception) => println(s"Failed to send message to channel $channelId: ${exception.getMessage}")
    }
  }


  def onMessageSent(channelId: String): Unit = {
    Thread.sleep(10 * 1000) // Wait for 10 seconds before closing the channel
    channelApiClient.closeChannel(channelId).onComplete {
      case scala.util.Success(_) => println(s"Channel $channelId closed successfully")
      case scala.util.Failure(exception) => println(s"Failed to close channel $channelId: ${exception.getMessage}")
    }
  }

  def handleRequest(): Unit = {
    val channelFuture = channelApiClient.createChannel()
    channelFuture.onComplete {
      case scala.util.Success(channelId) => onChannelCreated(channelId)
      case scala.util.Failure(exception) => println(s"Failed to create channel: ${exception.getMessage}")
    }
     // Keep the main thread alive to allow the async operation to complete
    Thread.sleep(30000) 
  }
}

