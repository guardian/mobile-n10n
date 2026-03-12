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
import scala.concurrent.duration.DurationInt

class BroadcastApiClient {
  
  private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  println("HttpClient in BroadcastApiClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"

  private val bundleId = "uk.co.guardian.iphone2.debug"
  
  private val url = s"https://api.sandbox.push.apple.com:443//4/broadcasts/apps/$bundleId"

  private val message = 
    """{ 
    |  \"aps\": {
    |     \"timestamp\": 1685952000,
    |     \"event\": \"update\",
    |     \"content-state\": {
    |         \"currentHealthLevel\": 0.0,
    |         \"eventDescription\": \"Power Panda has been knocked down!\"
    |     },
    |     \"alert\": {
    |         \"title\": {
    |             \"loc-key\": \"%@ is knocked down!\",
    |             \"loc-args\": [\"Power Panda\"]
    |         },
    |         \"body\": {
    |             \"loc-key\": \"Use a potion to heal %@!\",
    |             \"loc-args\": [\"Power Panda\"]
    |         },
    |         \"sound\": \"HeroDown.mp4\"
    |     }
    |  }
    }"""

  private def getAccessToken(): String = {
    return "invalid-token-for-testing"
  }

  private def generateToken(): String = {
    val signingKey = ApnsSigningKey.loadFromPkcs8File(
      new java.io.File("liveactivities/src/main/resources/AuthKey_N9MYT8RFH4.p8"),
      "998P9U5NGJ",
      "N9MYT8RFH4"
    )
    val authenticationToken = new AuthenticationToken(signingKey, new Date())
    return authenticationToken.getAuthorizationHeader().toString()
  }

  def sendToChannel(channelId: String, expiration: Option[Instant], priority: Option[Int]): Future[String] = {
    println(s"Broadcasting to channel $channelId")
    val authToken = generateToken()
    println(s"Generated auth token: $authToken")

    val request: HttpRequest = HttpRequest.newBuilder(new URI(url))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", authToken)
      .header("Content-Type", mediaType)
      .header("apns-expiration", expiration.getOrElse(Instant.now().plusSeconds(5 * 60)).getEpochSecond.toString)
      .header("apns-priority", priority.map(_.toString).getOrElse("1"))
      .header("apns-push-type", "Liveactivity")
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
