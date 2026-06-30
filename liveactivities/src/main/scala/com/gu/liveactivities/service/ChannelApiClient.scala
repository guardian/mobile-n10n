package com.gu.liveactivities.service

import java.net.http.HttpClient
import java.time.Duration
import java.nio.charset.StandardCharsets
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import scala.concurrent.Future
import scala.concurrent.Promise

import play.api.libs.json.{Json, Reads}
import com.gu.liveactivities.util.Logging

class ChannelApiClient(authentication: Authentication, bundleId: String, sendingToProdServer: Boolean) extends Logging {
  
  private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  logger.info("HttpClient in ChannelApiClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"

  private val url = if (sendingToProdServer)
    s"https://api-manage-broadcast.push.apple.com:2196/1/apps/$bundleId"
  else
    s"https://api-manage-broadcast.sandbox.push.apple.com:2195/1/apps/$bundleId"

  private val message = "{\"message-storage-policy\": 1, \"push-type\": \"LiveActivity\"}"

  def createChannel(liveActivityId: String): Future[String] = {
    logger.info("Creating channel")
    val authToken = authentication.getAccessToken()
    val request: HttpRequest = HttpRequest.newBuilder(new URI(s"${url}/channels"))
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

  def closeChannel(channelId: String, liveActivityId: String): Future[Unit] = {
    logger.info(s"Closing channel $channelId")
    val authToken = authentication.getAccessToken()
    val request: HttpRequest = HttpRequest.newBuilder(new URI(s"${url}/channels"))
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
        logger.error(liveActivityMarker(liveActivityId), s"Failed to close channel $channelId due to error ${err.getMessage}")
        p.failure(err)
      } else if (response.statusCode() >= 200 && 
                response.statusCode() < 300) {
        logger.info(liveActivityMarker(liveActivityId), s"Channel closed successfully with channel ID $channelId")
        p.success(())
      } else {
        logger.error(liveActivityMarker(liveActivityId), s"Failed to close channel with status code ${response.statusCode()} and body ${response.body()}")
        p.failure(new Exception(s"Failed to close channel with status code ${response.statusCode()} and body ${response.body()}"))
      }
    })
    p.future
  }

  /////////////////////////////////////////////////////////////////
  case class AllChannelsResponse(channels: List[String])
  object AllChannelsResponse {
    implicit val reads: Reads[AllChannelsResponse] = Json.reads[AllChannelsResponse]
  }

  def getAllChannels(): Future[List[String]] = {
    logger.info("Fetching all channels")

    val authToken = authentication.getAccessToken()
    val request: HttpRequest = HttpRequest.newBuilder(new URI(s"${url}/all-channels"))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", authToken)
      .header("Content-Type", mediaType)
      .GET()
      .timeout(Duration.ofSeconds(60))
      .build()

    val p = Promise[List[String]]()
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) => {
      if (response == null) {
        logger.error(s"Failed to get all channels due to error ${err.getMessage}")
        p.failure(err)
      } else if (response.statusCode() >= 200 &&
                response.statusCode() < 300) {
        val channelIds = Json.parse(response.body()).as[AllChannelsResponse].channels
        logger.info(s"Retrieved all channels successfully: ${channelIds.length}")
        p.success(channelIds)
      } else {
        logger.error(s"Failed to get all channels with status code ${response.statusCode()} and body ${response.body()}")
        p.failure(new Exception(s"Failed to get all channels with status code ${response.statusCode()} and body ${response.body()}"))
      }
    })
    p.future
  }

}
