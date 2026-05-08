package com.gu.liveactivities.service

import com.gu.liveactivities.models.BroadcastBody

import java.net.http.HttpClient
import java.time.Duration
import java.nio.charset.StandardCharsets
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import scala.concurrent.Future
import scala.concurrent.Promise
import play.api.libs.json.Json
import com.gu.liveactivities.util.Logging

import java.time.Instant

class BroadcastApiClient(
    authentication: Authentication,
    bundleId: String,
    sendingToProdServer: Boolean
) extends Logging {

  private val httpClient: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  logger.info("HttpClient in BroadcastApiClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"

  // TODO - to check
  private val serviceEndpoint =
    if (sendingToProdServer)
      s"https://api.sandbox.push.apple.com:443/4/broadcasts/apps/$bundleId"
    else
      s"https://api.sandbox.push.apple.com:443/4/broadcasts/apps/$bundleId"

  def sendToChannel(
      channelId: String,
      expiration: Option[Instant],
      priority: Option[Int],
      broadcastPayload: BroadcastBody
  ): Future[String] = {
    logger.info(s"Broadcasting to channel $channelId for match")

    val broadcastPayloadJson: String = Json.stringify(
      Json.toJson(broadcastPayload)(BroadcastBody.broadcastBodyFormat)
    )

    val authToken = authentication.getAccessToken()
    val request: HttpRequest = HttpRequest
      .newBuilder(new URI(serviceEndpoint))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", authToken)
      .header("Content-Type", mediaType)
      .header("apns-channel-id", channelId)
      .header(
        "apns-expiration",
        expiration
          .getOrElse(Instant.now().plusSeconds(5 * 60))
          .getEpochSecond
          .toString
      )
      .header("apns-priority", priority.map(_.toString).getOrElse("1"))
      .header("apns-push-type", "Liveactivity")
      .POST(HttpRequest.BodyPublishers.ofString(broadcastPayloadJson, charSet))
      .timeout(Duration.ofSeconds(60))
      .build()

    val p = Promise[String]()
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) => {
      logger.info(s"Received response: $response, error: $err")

      if (err != null) {
        logger.error("Error occurred while sending broadcast", err)
        p.failure(err)
      } else if (response.statusCode() != 200) {
        logger.error(s"Failed to send broadcast with status code ${response.statusCode()} and body ${response.body()}")
        p.failure(new RuntimeException(s"Failed to send broadcast with status code ${response.statusCode()} and body ${response.body()}"))
      } else {
        val apnsUniqueId = response.headers().firstValue("apns-unique-id").orElse("not found") // SANDBOX only header for debugging
        logger.info(s"Received response with status code ${response.statusCode()} and apns-unique-id ${apnsUniqueId}")

        p.success(response.body())
      }
    })
    p.future
  }
}
