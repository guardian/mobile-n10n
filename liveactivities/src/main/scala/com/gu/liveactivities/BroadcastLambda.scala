package com.gu.liveactivities

import com.gu.liveactivities.service.BroadcastApiClient
import scala.concurrent.Await
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.Context
import play.api.libs.json.Format
import play.api.libs.json.Json
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import java.io.{InputStream, OutputStream}
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import java.nio.charset.StandardCharsets

// TODO - we should get the channel ID by looking up the match ID in the datastore
case class BroadcastRequest(matchId: String, channelId: String, payload: String)

object BroadcastRequest {
	implicit val jf: Format[BroadcastRequest] = Json.format[BroadcastRequest]
}

object BroadcastLambda extends RequestStreamHandler{

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val broadcastApiClient = new BroadcastApiClient()

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[BroadcastRequest] match {
      case JsSuccess(request, _) => {
        processRequest(request, context)
        output.write(Json.toJson("Broadcast sent").toString().getBytes(StandardCharsets.UTF_8))
      }
      case JsError(errors) => {
        println(s"Failed to parse request: $errors")
        output.write(Json.toJson("Invalid request").toString().getBytes(StandardCharsets.UTF_8))
      }
    }
  }

  def processRequest(request: BroadcastRequest, context: Context): Unit = {
    // TODO - determine expiry time and priority
    val broadcastFuture = broadcastApiClient.sendToChannel(request.channelId, None, None)
    // TODO - the timeout value
    Try(Await.result(broadcastFuture, scala.concurrent.duration.Duration.Inf)) match {
      case Success(apnResponse) => println(s"Sent broadcast successfully with Response: $apnResponse")
      case Failure(exception) => println(s"Failed to send broadcast: ${exception.getMessage}")
    }
  }
}
