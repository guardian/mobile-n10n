package com.gu.liveactivities

import com.gu.liveactivities.service.BroadcastApiClient
import scala.concurrent.Await
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.Context

// TODO - we should get the channel ID by looking up the match ID in the datastore
case class BroadcastRequest(matchId: String, channelId: String, payload: String)

object BroadcastLambda extends RequestHandler[BroadcastRequest, Unit]{

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val broadcastApiClient = new BroadcastApiClient()

  def handleRequest(request: BroadcastRequest, context: Context): Unit = {
    // TODO - determine expiry time and priority
    val broadcastFuture = broadcastApiClient.sendToChannel(request.channelId, None, None)
    // TODO - the timeout value
    Try(Await.result(broadcastFuture, scala.concurrent.duration.Duration.Inf)) match {
      case Success(apnResponse) => println(s"Sent broadcast successfully with Response: $apnResponse")
      case Failure(exception) => println(s"Failed to send broadcast: ${exception.getMessage}")
    }
  }
}
