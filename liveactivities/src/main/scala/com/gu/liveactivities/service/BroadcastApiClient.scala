package com.gu.liveactivities.service

import java.net.http.HttpClient
import java.time.Duration
import java.nio.charset.StandardCharsets
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import scala.concurrent.Future
import scala.concurrent.Promise
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.auth.AuthenticationToken
import play.api.libs.json.Json
import com.gu.liveactivities.BroadcastFixtures._
import com.gu.liveactivities.models.BroadcastJsonFormats._

import java.time.Instant
import java.util.Date
import com.gu.liveactivities.models.BroadcastJsonFormats

class BroadcastApiClient {
  
  private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  println("HttpClient in BroadcastApiClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"

  private val bundleId = "uk.co.guardian.iphone2.debug"
  
  private val url = s"https://api.sandbox.push.apple.com:443/4/broadcasts/apps/$bundleId"

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

  private val startPayload: String = Json.stringify(Json.toJson(broadcastStartBodyFixture)(BroadcastJsonFormats.broadcastStartBodyFormat))
  private val updatePayload: String = Json.stringify(Json.toJson(broadcastUpdateBodyFixture)(BroadcastJsonFormats.broadcastUpdateBodyFormat))
  private val endPayload: String = Json.stringify(Json.toJson(broadcastEndBodyFixture)(BroadcastJsonFormats.broadcastEndBodyFormat))

  def sendToChannel(channelId: String, expiration: Option[Instant], priority: Option[Int]): Future[String] = {
    println(s"Broadcasting to channel $channelId")
    val authToken = Authentication.getAccessToken()
    println(s"Generated auth token: $authToken")

    val request: HttpRequest = HttpRequest.newBuilder(new URI(url))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", authToken)
      .header("Content-Type", mediaType)
      .header("apns-channel-id", channelId)
      .header("apns-expiration", expiration.getOrElse(Instant.now().plusSeconds(5 * 60)).getEpochSecond.toString)
      .header("apns-priority", priority.map(_.toString).getOrElse("1"))
      .header("apns-push-type", "Liveactivity")
      .POST(HttpRequest.BodyPublishers.ofString(updatePayload, charSet))

      .timeout(Duration.ofSeconds(60))
      .build()

    val p = Promise[String]()
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) => {
      println(s"Received response: $response, error: $err")

      if (err != null) {
        p.failure(err)
      } else if (response.statusCode() != 200) {
        p.failure(new RuntimeException(s"Failed to send broadcast with status code ${response.statusCode()} and body ${response.body()}"))
      } else {
        val apnsUniqueId = response.headers().firstValue("apns-unique-id").orElse("not found") // SANDBOX only header for debugging
        println(s"Received response with status code ${response.statusCode()} and apns-unique-id ${apnsUniqueId}")

        p.success(response.body())
      }
    })
    p.future
  }
}
