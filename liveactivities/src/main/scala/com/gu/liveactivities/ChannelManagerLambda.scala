package com.gu.liveactivities

import com.gu.liveactivities.service.{ChannelApiClient, BroadcastApiClient, Authentication}
import scala.concurrent.Await
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.Context

// TODO - we should get the channel ID by looking up the match ID in the datastore
case class ChannelRequest(matchId: String, channelId: String, toCreate: Boolean)

object ChannelManagerLambda extends RequestHandler[ChannelRequest, String] {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val channelApiClient = new ChannelApiClient()
  val broadcastApiClient = new BroadcastApiClient()

  def onChannelCreated(matchId: String, channelId: String): String = {
    // TODO - update dynamo table
    println(s"Channel created with ID: $channelId for match ID: $matchId")
    return channelId
  }

  def onChannelClosed(matchId: String, channelId: String): String = {
    // TODO - update dynamo table
    println(s"Channel closed with ID: $channelId for match ID: $matchId")
    return channelId
  }

  def handleRequest(request: ChannelRequest, context: Context): String = {
    if (request.toCreate) {
      println(s"Received request to create channel for match ID ${request.matchId}")
      val channelFuture = channelApiClient.createChannel()
      // TODO - the timeout value
      Try(Await.result(channelFuture, scala.concurrent.duration.Duration.Inf)) match {
        case Success(channelId) => onChannelCreated(request.matchId, channelId)
        case Failure(exception) => {
          println(s"Failed to create channel: ${exception.getMessage}")
          exception.getMessage
        }
      }
    } else {
      println(s"Received request to delete channel for match ID ${request.matchId}")
      val channelFuture = channelApiClient.closeChannel(request.channelId)
      // TODO - the timeout value
      Try(Await.result(channelFuture, scala.concurrent.duration.Duration.Inf)) match {
        case Success(_) => onChannelClosed(request.matchId, request.channelId)
        case Failure(exception) => {
          println(s"Failed to close channel: ${exception.getMessage}")
          exception.getMessage
        }
      }
    }
  }
}
