package com.gu.liveactivities

import play.api.libs.json.Json
import scala.io.Source
import java.nio.charset.StandardCharsets

/**
 *  Local run creates and closes a channel on the APNS Sandbox env for the iOS debug app.
 */
object ChannelManagerLambdaLocalRun extends App {

  println("Start running ChannelManagerLambda locally")

  val createChannelJson = Source.fromResource("channel-create-payload.json").mkString

  val createRequestStream = new java.io.ByteArrayInputStream(createChannelJson.getBytes(StandardCharsets.UTF_8))

  val createResponseStream = new java.io.ByteArrayOutputStream()

  ChannelManagerLambda.handleRequest(createRequestStream, createResponseStream, null)

  val closeChannelJson = Source.fromResource("channel-delete-payload.json").mkString

  val closeRequestStream = new java.io.ByteArrayInputStream(closeChannelJson.getBytes(StandardCharsets.UTF_8))

  val closeResponseStream = new java.io.ByteArrayOutputStream()

  ChannelManagerLambda.handleRequest(closeRequestStream, closeResponseStream, null)

  println("Finished running ChannelManagerLambda locally with response: " + closeResponseStream.toString())
}

/**
 * Local run broadcasts a live activity update on the APNS Sandbox env for the iOS debug app.
 * A local Dynamo table of channel mappings is needed or amend Lambda.scala to point to CODE Dynamo table.
 */
object BroadcastLambdaLocalRun extends App {

  println("Start running BroadcastLambda locally")

  val broadcastRequest = Source.fromResource("broadcast-update-payload.json").mkString // eventbridge event represented by liveActivityPayload

  val broadcastRequestStream = new java.io.ByteArrayInputStream(broadcastRequest.getBytes(StandardCharsets.UTF_8))

  val broadcastResponseStream = new java.io.ByteArrayOutputStream()

  BroadcastLambda.handleRequest(broadcastRequestStream, broadcastResponseStream, null)

  println("Finished running BroadcastLambda locally with response: " + broadcastResponseStream.toString())
}