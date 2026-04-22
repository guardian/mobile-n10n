package com.gu.liveactivities

import play.api.libs.json.Json
import java.nio.charset.StandardCharsets

/**
 *  Local run creates and closes a channel on the APNS Sandbox env for the iOS debug app.
 */
object ChannelManagerLambdaLocalRun extends App {

  val channelId = "match123"

  println("Start running ChannelManagerLambda locally")

  val createChannelJson = Json.toJson(ChannelRequest(channelId, Some("competiton-001"), None, toCreate = true)).toString()

  val createRequestStream = new java.io.ByteArrayInputStream(createChannelJson.getBytes(StandardCharsets.UTF_8))

  val createResponseStream = new java.io.ByteArrayOutputStream()

  ChannelManagerLambda.handleRequest(createRequestStream, createResponseStream, null)

  val closeRequest = ChannelRequest(channelId, None, None, toCreate = false)

  val closeRequestStream = new java.io.ByteArrayInputStream(Json.toJson(closeRequest).toString().getBytes(StandardCharsets.UTF_8))

  val closeResponseStream = new java.io.ByteArrayOutputStream()

  ChannelManagerLambda.handleRequest(closeRequestStream, closeResponseStream, null)

  println("Finished running ChannelManagerLambda locally with response: " + closeResponseStream.toString())
}

/**
 * Local run broadcasts a live activity update on the APNS Sandbox env for the iOS debug app.
 */
object BroadcastLambdaLocalRun extends App {

  println("Start running BroadcastLambda locally")

  val broadcastRequest =
    """{
    "matchId": "match-test-6",
    "payload": "test message",
    "eventId": "event-007",
    "eventTime": "2026-04-07T17:37:29.278+0000"
  }"""

  val broadcastRequestStream = new java.io.ByteArrayInputStream(broadcastRequest.getBytes(StandardCharsets.UTF_8))

  val broadcastResponseStream = new java.io.ByteArrayOutputStream()

  BroadcastLambda.handleRequest(broadcastRequestStream, broadcastResponseStream, null)

  println("Finished running BroadcastLambda locally with response: " + broadcastResponseStream.toString())
}