package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import software.amazon.awssdk.services.eventbridge.EventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.{PutEventsRequest, PutEventsRequestEntry}

import scala.util.Try

class PollingLiveGamesDataLambda extends RequestHandler[java.util.Map[String, Any], Unit] {

  override def handleRequest(input: java.util.Map[String, Any], context: Context): Unit =
    PollingLiveGamesDataLambda.handleRequest()
}


object PollingLiveGamesDataLambda {

  private val eventBusName = "liveactivities-eventbus-CODE" // TODO - move to config
  private val eventBridgeClient = EventBridgeClient.builder().build() // credentials? is cdk policy roll enough?

  def handleRequest(): Unit = {

    // TODO
    // schedule lambda to run every 15seconds via cdk.
    // Step 1 read liveActivity channel mapping dynamo table to get any match ids that are live.
    // live match no? - do nothing.
    // live match yes?
    // Step 2 - publish event to event bridge with match id
    // - collect unique competition ids for live matches
    // - call PA /liveGames for all those competition ids that have live matches
    // - send event to eventbus with match id and channel id.

    // iteration:  tbc diff pa events to check if we need to send event or not??

    val result = Try {

      val entry = PutEventsRequestEntry
        .builder()
        .source("pa-live-game-updates")
        .detailType("football-live-game")
        .detail(
          """{"matchId": "12345", "status": "live", "homeScore": 1, "awayScore": 0}""" // dummy data PA data will go heare
        )
        .eventBusName(eventBusName)
        .build()

      val request = PutEventsRequest
        .builder()
        .entries(entry)
        .build()

      val response = eventBridgeClient.putEvents(request)
      println(
        s"Event published. Failed entry count: ${response.failedEntryCount()}"
      )
    }

    result.failed.foreach(e =>
      println(s"Failed to publish event: ${e.getMessage}")
    )
  }
}
