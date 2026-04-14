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
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext

class LiveActivityPusher extends Logging {

  private val eventBusName =
    "liveactivities-eventbus-CODE"
  private val eventBridgeClient =
    EventBridgeClient
      .builder()
      .build()

  def pushEvents(events: List[LiveActivityPayload])(implicit ec: ExecutionContext): Future[Unit] = {

    logger.info(
      "Eventbus pusher: number of events to push: " + events.size
    )
    Future.traverse(events)(pushToEventbus).map(_ => ())
  }

  def pushToEventbus(payload: LiveActivityPayload)(implicit ec: ExecutionContext): Future[Unit] = {

    logger.info(
      s"Eventbus pusher: Processing event with id ${payload.id}"
    )
    val jsonDetail = Json.toJson(payload).toString()
    if (jsonDetail.isEmpty || jsonDetail == "{}") {
      logger.info(
        s"Eventbus pusher: Skipping empty event for ${payload.id}"
      )
      Future.successful(())
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

      result match {
        case Success(_) => {
          logger.info(
            s"Eventbus pusher: Successfully processed event with id ${payload.id}"
          )
          Future.successful(())
        }
        case Failure(e) => {
          logger.error(
            s"Eventbus pusher: Failed to publish event with id ${payload.id}: ${e.getMessage}"
          )
          Future.failed(e)
        }
      }
    }
  }
}
