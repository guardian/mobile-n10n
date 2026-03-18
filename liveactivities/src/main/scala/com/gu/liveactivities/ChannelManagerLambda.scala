package com.gu.liveactivities

import com.gu.liveactivities.service.{ChannelApiClient, BroadcastApiClient, Authentication}
import com.gu.liveactivities.util.{Configuration, IosConfiguration}
import scala.concurrent.Await
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import java.io.{InputStream, OutputStream}
import play.api.libs.json.Json
import play.api.libs.json.Format
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import java.nio.charset.StandardCharsets

// TODO - we should get the channel ID by looking up the match ID in the datastore
case class ChannelRequest(matchId: String, channelId: String, toCreate: Boolean)

object ChannelRequest {
  implicit val jf: Format[ChannelRequest] = Json.format[ChannelRequest]
}

object ChannelManagerLambda extends RequestStreamHandler {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val config: IosConfiguration = Configuration.fetchIos()

  val authentication = new Authentication(config.teamId, config.keyId, config.certificate)

  val channelApiClient = new ChannelApiClient(authentication, config.bundleId, config.sendingToProdServer)

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

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[ChannelRequest] match {
      case JsSuccess(request, _) => {
        val response = processRequest(request, context)
        output.write(Json.toJson(response).toString().getBytes(StandardCharsets.UTF_8))
      }
      case JsError(errors) => {
        println(s"Failed to parse request: $errors")
        output.write(Json.toJson("Invalid request").toString().getBytes(StandardCharsets.UTF_8))
      }
    }
  }

  def processRequest(request: ChannelRequest, context: Context): String = {
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
