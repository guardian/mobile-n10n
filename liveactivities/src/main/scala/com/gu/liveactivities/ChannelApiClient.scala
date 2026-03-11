package com.gu.liveactivities

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

class ChannelApiClient {
  
  private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  println("HttpClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"
  
  private val url = "https://api-manage-broadcast.sandbox.push.apple.com:2195/1/apps/<bundleId>/channels"

  private val message = "{\"message-storage-policy\": 1, \"push-type\": \"LiveActivity\"}"

  private def getAccessToken(): String = {
    return "invalid-token-for-testing"
  }

  private def generateToken(): String = {
    val signingKey = ApnsSigningKey.loadFromPkcs8File(
      new java.io.File("src/main/resources/AuthKey_XXXXXXXXXX.p8"),
      "TEAMID",
      "KEYID"
    )
    val authenticationToken = new AuthenticationToken(signingKey, new Date())
    return authenticationToken.getAuthorizationHeader().toString()
  }

  def createChannel(): Future[String] = {
    println("Creating channel")

    val request: HttpRequest = HttpRequest.newBuilder(new URI(url))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", "Bearer " + getAccessToken())
      .header("Content-Type", mediaType)
      .POST(HttpRequest.BodyPublishers.ofString(message, charSet))
      .timeout(Duration.ofSeconds(60))
      .build()
    val p = Promise[String]()
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) => {
      if (response == null) {
        p.failure(err)
      } else {
        println(s"Received response with status code ${response.statusCode()} and body ${response.body()}")
        p.success(response.body())
      }
    })
    p.future
  }
}
