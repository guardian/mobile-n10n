package com.gu.liveactivities.service

import java.net.http.HttpClient
import java.time.Duration
import java.nio.charset.StandardCharsets
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import scala.concurrent.Future
import scala.concurrent.Promise

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.auth.AuthenticationToken;
import java.time.Instant
import java.util.Date
import com.gu.liveactivities.util.Logging

class ChannelApiClient(authentication: Authentication, bundleId: String, sendingToProdServer: Boolean) extends Logging {
  
  private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  logger.info("HttpClient in ChannelApiClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"
 
  // TODO - to check
  private val url = if (sendingToProdServer)
    s"https://api-manage-broadcast.sandbox.push.apple.com:2195/1/apps/$bundleId/channels"
  else
    s"https://api-manage-broadcast.sandbox.push.apple.com:2195/1/apps/$bundleId/channels"


  private val message = "{\"message-storage-policy\": 1, \"push-type\": \"LiveActivity\"}"

  def createChannel(): Future[String] = {
    logger.info("Creating channel")
    val authToken = authentication.getAccessToken()
    val request: HttpRequest = HttpRequest.newBuilder(new URI(url))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", authToken)
      .header("Content-Type", mediaType)
      .POST(HttpRequest.BodyPublishers.ofString(message, charSet))
      .timeout(Duration.ofSeconds(60))
      .build()
    val p = Promise[String]()
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) => {
      if (response == null) {
        p.failure(err)
      } else if (response.statusCode() >= 200 && 
                response.statusCode() < 300 && 
                response.headers().firstValue("apns-channel-id").isPresent()) {
        val channelId = response.headers().firstValue("apns-channel-id").get()
        logger.info(s"Channel created successfully with channel ID $channelId")
        p.success(channelId)
      } else {
        logger.error(s"Failed to create channel with status code ${response.statusCode()} and body ${response.body()}")
        p.failure(new Exception(s"Failed to create channel with status code ${response.statusCode()} and body ${response.body()}"))
      }
    })
    p.future
  }

  def closeChannel(channelId: String): Future[Unit] = {
    logger.info(s"Closing channel $channelId")
    val authToken = authentication.getAccessToken()
    val request: HttpRequest = HttpRequest.newBuilder(new URI(url))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", authToken)
      .header("Content-Type", mediaType)
      .header("apns-channel-id", channelId)
      .DELETE()
      .timeout(Duration.ofSeconds(60))
      .build()
    val p = Promise[Unit]()
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) => {
      if (response == null) {
        logger.error(s"Failed to close channel $channelId due to error ${err.getMessage}")
        p.failure(err)
      } else if (response.statusCode() >= 200 && 
                response.statusCode() < 300) {
        logger.info(s"Channel closed successfully with channel ID $channelId")
        p.success(())
      } else {
        logger.error(s"Failed to close channel with status code ${response.statusCode()} and body ${response.body()}")
        p.failure(new Exception(s"Failed to close channel with status code ${response.statusCode()} and body ${response.body()}"))
      }
    })
    p.future
  } 

}
