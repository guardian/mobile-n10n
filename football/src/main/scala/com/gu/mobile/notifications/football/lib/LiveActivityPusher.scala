package com.gu.mobile.notifications.football.lib

import com.gu.mobile.notifications.client.models.liveActitivites._
import com.gu.mobile.notifications.football.Logging
import com.gu.mobile.notifications.football.models.MatchDataWithArticle
import play.api.libs.json.Json
import software.amazon.awssdk.services.eventbridge.EventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.{
  PutEventsRequest,
  PutEventsRequestEntry,
  PutEventsResponse
}

import scala.util.Try

// Temporary json formatters to test pushing match data - will be replaced once we know what we want to send.
import play.api.libs.json.Writes

class LiveActivityPusher extends Logging {

  private val eventBusName =
    "liveactivities-eventbus-CODE"
  private val eventBridgeClient =
    EventBridgeClient
      .builder()
      .build()

  def pushToEventbus(events: List[LiveActivityPayload]) = {

    logger.info(
      "Eventbus pusher: number of events to push: " + events.size
    )
    events.map(payload => {
      logger.info(
        s"Eventbus pusher: Processing event with id ${payload.eventId}"
      )

      val jsonDetail = Json.toJson(payload).toString()
      if (jsonDetail.isEmpty || jsonDetail == "{}") {
        logger.info(
          s"Eventbus pusher: Skipping empty event for ${payload.eventId}"
        )
      } else {

        // TODO for the eventbus to direct the event to the right place
        val activityType = payload.eventType match {
          case CreateChannel      => "channel-create"
          case StartLiveActivity  => "broadcast-start"
          case UpdateLiveActivity => "broadcast-update"
          case EndLiveActivity    => "broadcast-end"
          case DeleteChannel      => "channel-delete"
          case _ => "unknown"
        }

        val result = Try {
          val entry = PutEventsRequestEntry
            .builder()
            .source("football-lambda")
            .detailType(activityType)
            .detail(Json.toJson(payload).toString())
            .eventBusName(eventBusName)
            .build()

          val request = PutEventsRequest
            .builder()
            .entries(entry)
            .build()

          val response: PutEventsResponse = eventBridgeClient.putEvents(request)
          logger.info(
            s"Eventbus pusher: Event published. Failed entry count: ${response.failedEntryCount()}"
          )
          logger.info(
            s"Eventbus pusher: Event details: ${Json.toJson(payload).toString()}"
          )
        }

        result.failed.foreach(e =>
          logger.error(
            s"Eventbus pusher: Failed to publish event: ${e.getMessage}"
          )
        )

      }
    })
  }
}
