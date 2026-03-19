package com.gu.liveactivities

import play.api.libs.json.Json
import java.nio.charset.StandardCharsets

object LambdaLocalRun extends App {

  println("Start running ChannelManagerLambda locally")
  val createChannelJson = Json.toJson(ChannelRequest("match123", "channel123", toCreate = true)).toString()

  val createRequestStream = new java.io.ByteArrayInputStream(createChannelJson.getBytes(StandardCharsets.UTF_8))

  val createResponseStream = new java.io.ByteArrayOutputStream()

  ChannelManagerLambda.handleRequest(createRequestStream, createResponseStream, null)

  val channelId = Json.parse(createResponseStream.toString()).as[String]

  val closeRequest = ChannelRequest("match123", channelId, toCreate = false)

  val closeRequestStream = new java.io.ByteArrayInputStream(Json.toJson(closeRequest).toString().getBytes(StandardCharsets.UTF_8))

  val closeResponseStream = new java.io.ByteArrayOutputStream()

  ChannelManagerLambda.handleRequest(closeRequestStream, closeResponseStream, null)

  println("Finished running ChannelManagerLambda locally with response: " + closeResponseStream.toString())
}
