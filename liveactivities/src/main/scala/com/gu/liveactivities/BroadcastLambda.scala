package com.gu.liveactivities

import com.gu.liveactivities.service.BroadcastApiClient

import scala.concurrent.Await
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler

import java.io.{InputStream, OutputStream}
import play.api.libs.json._
import com.gu.liveactivities.util.Logging

import scala.concurrent.Future
import com.gu.liveactivities.models.LiveActivityInvalidStateException
import com.gu.liveactivities.models.BroadcastBody

import java.time.ZonedDateTime
import com.gu.liveactivities.util.DateTimeHelper.{dateTimeFromLong, dateTimeToLong, dateTimeToString}
import com.gu.mobile.notifications.client.models.liveActitivites.{EndLiveActivityEvent, EventBridgeEvent, FootballMatchContentState, LiveActivityPayload, UpdateLiveActivityEvent}

import scala.concurrent.duration.DurationInt



object BroadcastLambda extends RequestStreamHandler with Lambda with Logging {

  val broadcastApiClient = new BroadcastApiClient(authentication, config.bundleId, config.sendingToProdServer)

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[EventBridgeEvent] match {
      case JsSuccess(request, _) => {

        // If we see these errors there is a misconfiguration in the eventbridge routing rules.
        if (request.`detail-type` != UpdateLiveActivityEvent && request.`detail-type` != EndLiveActivityEvent) {
          logger.error(s"Unexpected eventbridge event type: ${request.`detail-type`}")
          throw new Exception(s"Unexpected event type: ${request.`detail-type`}")
        }

        val payload = request.detail // LiveActivityPayload

        processRequest(payload, context)
      }
      case JsError(errors) => {
        logger.error(s"Failed to parse request: $errors")
        throw new Exception(s"Invalid request: $errors")
      }
    }
  }

  def processRequest(requestPayload: LiveActivityPayload, context: Context): Unit = {
    logger.info(s"Received request to broadcast payload for match ID ${requestPayload.liveActivityID}")

    // todo this needs to be tidied to support live activities other than football
    val matchId: String = requestPayload.liveActivityID
    val eventId: String = requestPayload.id.toString
    val eventTime: ZonedDateTime = dateTimeFromLong(requestPayload.eventTimestamp) // time triggering event was received and processed in football lambda
    val contentState: FootballMatchContentState = requestPayload.broadcastContentStateData match {
      case Some(cs: FootballMatchContentState) => cs
      case Some(other) =>
        throw new Exception(s"Unexpected content state type: ${other.getClass.getSimpleName}")
      case None =>
        throw new Exception(s"Missing content state for match ID $matchId")
    }


    val shouldEndBroadcast: Boolean = requestPayload.eventType match {
      case EndLiveActivityEvent => true
      case UpdateLiveActivityEvent => false
      case _ =>
        logger.error(s"Unexpected event type ${requestPayload.eventType} for broadcast payload")
        throw new Exception(s"Unexpected event type ${requestPayload.eventType} for broadcast payload")

    }

    val broadcastFuture = for {
      mapping <- repository.getMappingById(matchId)
      _ <- if (!mapping.isChannelActive) {
          logger.error(s"Channel not active for match ID $matchId")
          Future.failed(new LiveActivityInvalidStateException(matchId, "Channel not active"))
        } else Future.successful(())

      _ <- if (!mapping.isLive) {
          logger.error(s"Event not live for match ID $matchId")
          Future.failed(new LiveActivityInvalidStateException(matchId, "Event not live"))
        } else Future.successful(())

      _ <- if (mapping.lastEventId.contains(eventId)) {
          logger.warn(s"Duplicate event ID $eventId for match ID $matchId")
          Future.failed(new LiveActivityInvalidStateException(matchId, "Duplicate event ID"))
        } else Future.successful(())

      _ <- if (mapping.lastEventAt.exists(lastEventAt => eventTime.isBefore(lastEventAt))) {
          logger.warn(s"Out of order event time ${dateTimeToString(eventTime)} for match ID $matchId")
          Future.failed(new LiveActivityInvalidStateException(matchId, "Out of order event time"))
        } else Future.successful(())

      // TODO - determine expiry time and priority

      _ = logger.info(s"Sending broadcast for match ID $matchId to channel ID ${mapping.channelId}")
      broadcastPayload = BroadcastBody(contentState, shouldEndBroadcast)
      _ <- broadcastApiClient.sendToChannel(mapping.channelId, None, None, broadcastPayload)
      _ = logger.info(s"Broadcast ${if(shouldEndBroadcast)"END"} sent successfully for match ID $matchId to channel ID ${mapping.channelId}")

      _ <- repository.updateMappingLastEvent(matchId, Some(eventId), Some(eventTime))
      _ = logger.info(s"Record updated successfully for match ID $matchId")
    } yield mapping.channelId

    //Timeout set to 160 seconds to provide a safety buffer.
    // While the sum of downstream timeouts is at most ~110s,
    // we allow extra time for other things such as JSON parsing, cold starts,
    // authentication token fetches, and potential network congestion
    // to ensure the Lambda doesn't fail a healthy request that is just running slow.
    Try(Await.result(broadcastFuture, 160.seconds)) match {

      case Success(_) => {
        // todo
        logger.info(s"Broadcast ${if(shouldEndBroadcast)"END"} successfully sent for liveActivityID $matchId")
      }
      case Failure(exception) => {
        logger.error(s"Failed to send broadcast for liveActivityID $matchId: ${exception.getMessage}")
        throw exception
      }
    }
  }
}
