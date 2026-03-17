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

object ChannelApiClient {

  var authenticationToken: Option[String] = None

  var issueDate: Option[Date] = None
}

class ChannelApiClient {
  
  private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  println("HttpClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"

  private val bundleId = "uk.co.guardian.iphone2.debug"
  
  private val url = s"https://api-manage-broadcast.sandbox.push.apple.com:2195/1/apps/$bundleId/channels"

  private val message = "{\"message-storage-policy\": 1, \"push-type\": \"LiveActivity\"}"

  def createChannel(): Future[String] = {
    println("Creating channel")
    val authToken = Authentication.getAccessToken()
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
        println(s"Channel created successfully with channel ID $channelId")
        p.success(channelId)
      } else {
        println(s"Failed to create channel with status code ${response.statusCode()} and body ${response.body()}")
        p.failure(new Exception(s"Failed to create channel with status code ${response.statusCode()} and body ${response.body()}"))
      }
    })
    p.future
  }

  def closeChannel(channelId: String): Future[Unit] = {
    println(s"Closing channel $channelId")
    val authToken = Authentication.getAccessToken()
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
        println(s"Failed to close channel $channelId due to error ${err.getMessage}")
        p.failure(err)
      } else if (response.statusCode() >= 200 && 
                response.statusCode() < 300) {
        println(s"Channel closed successfully with channel ID $channelId")
        p.success(())
      } else {
        println(s"Failed to close channel with status code ${response.statusCode()} and body ${response.body()}")
        p.failure(new Exception(s"Failed to close channel with status code ${response.statusCode()} and body ${response.body()}"))
      }
    })
    p.future
  } 

}
