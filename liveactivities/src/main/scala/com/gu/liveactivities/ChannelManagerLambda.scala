package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.gu.liveactivities.models.LiveActivityData
import com.gu.mobile.notifications.client.models.liveActitivites.ChannelManagerLambdaEventSource

import com.gu.liveactivities.service.ChannelApiClient
import com.gu.liveactivities.util.Logging
import com.gu.mobile.notifications.client.models.liveActitivites._
import play.api.libs.json.{JsError, JsSuccess, Json}

import java.io.{InputStream, OutputStream}
import java.util.{Date, UUID}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object ChannelManagerLambda extends RequestStreamHandler with Lambda with Logging {

  private val channelApiClient = new ChannelApiClient(authentication, config.bundleId, config.sendingToProdServer)

  private def processCreateChannelRequest(matchId: String, eventData: Option[LiveActivityData], broadcastContentStateData: Option[ContentState]): Future[String] = {
    logger.info(liveActivityMarker(matchId), s"Received request to create channel for match ID $matchId")

    val maybeChannelId = repository.containMapping(matchId)
    maybeChannelId.flatMap {
      case Some(id) =>
        logger.error(liveActivityMarker(matchId), s"Channel mapping already exists for match ID $matchId")
        // if channel id already exists for a match we don't want to fail the lambda to avoid the lambda retry on this event
        Future.successful(id)
      case None =>
        for {
          channelId <- channelApiClient.createChannel(matchId)
          _ <- repository.createMapping(matchId, channelId, eventData)
          _ = logger.info(liveActivityMarker(matchId), s"Channel created with channel ID $channelId for match ID $matchId")
          _ <- pushUpdateLiveActivityEvent(matchId, broadcastContentStateData)
          _ = logger.info(liveActivityMarker(matchId), s"Initial broadcast update pushed to event bus for channel ID $channelId and match ID $matchId")
        } yield channelId
    }
  }

  private def processCloseChannelRequest(matchId: String): Future[String] = {
    logger.info(liveActivityMarker(matchId), s"Channel closed for match ID: $matchId")
    val maybeMapping = repository.getMappingById(matchId)
    maybeMapping.flatMap{
      case mapping if !mapping.isChannelActive =>
        logger.error(liveActivityMarker(matchId), s"Channel not active for match ID $matchId")
        // if channel is inactive for a match we don't want to fail the lambda to avoid the lambda retry on this event
        Future.successful(mapping.channelId)
      case mapping =>  for {
        _ <- channelApiClient.closeChannel(mapping.channelId, matchId)
        _ <- repository.updateMappingActiveChannel(matchId, isActive = false)
        _ = logger.info(liveActivityMarker(matchId), s"Channel closed with channel ID ${mapping.channelId} for match ID $matchId")
      } yield mapping.channelId
    }
  }

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[EventBridgeEvent] match {
      case JsSuccess(request, _) =>
        request.`detail-type` match {
          case CreateChannelEvent | DeleteChannelEvent =>
            processRequest(request.detail, context)
          case other =>
            throw new Exception(s"Received unsupported event type: ${other.asString} for event ID ${request.id}")
        }
      case JsError(errors) =>
        logger.error(s"Failed to parse request: $errors")
        throw new Exception(s"Invalid request: $errors")
    }
  }

  // todo - consider error case: where a channel is created in APNS but the record update in Dynamo fails - consider cleanup mechanism for orphaned APNS channels.
  private def processRequest(request: LiveActivityPayload, context: Context): Unit = {
    val channelFuture = request.eventType match {
      case CreateChannelEvent =>

        if (!config.isEnabled) {
          logger.warn(liveActivityMarker(request.liveActivityID), s"Received ${CreateChannelEvent.asString} event for match ID ${request.liveActivityID} but channel creation is disabled by config")
          Future.unit
        } else {
          val eventData = request.broadcastContentStateData.map(LiveActivityData.toLiveActivityData)
          processCreateChannelRequest(request.liveActivityID, eventData, request.broadcastContentStateData)
        }

      case DeleteChannelEvent =>
        processCloseChannelRequest(request.liveActivityID)
      case other =>
        throw new Exception(s"Unsupported event type: ${other} for event ID ${request.id}")
    }

    //Timeout set to 160 seconds to provide a safety buffer.
    // While the sum of downstream timeouts is at most ~110s,
    // we allow extra time for other things such as JSON parsing, cold starts,
    // authentication token fetches, and potential network congestion
    // to ensure the Lambda doesn't fail a healthy request that is just running slow.
    Try(Await.result(channelFuture, 160.seconds)) match {
      case Success(_) => ()
      case Failure(exception) =>
        logger.error(liveActivityMarker(request.liveActivityID), s"Failed to process: ${exception.getMessage}")
        throw exception
    }
  }

  private def pushUpdateLiveActivityEvent(matchId: String, broadcastContentStateData: Option[ContentState]): Future[Unit] = {
    val triggeringEventId = s"football-match-status/$matchId/live-activity-initial-data"
    liveActivityPusher.pushToEventbus(ChannelManagerLambdaEventSource)(
      LiveActivityPayload(
        id = UUID.nameUUIDFromBytes(triggeringEventId.getBytes),
        eventType = UpdateLiveActivityEvent,
        liveActivityType = FootballLiveActivity,
        liveActivityID = matchId,
        dynamoStoreData = None,
        broadcastContentStateData = broadcastContentStateData,
        eventTimestamp =  new Date().getTime,
      )
    )
  }
}
