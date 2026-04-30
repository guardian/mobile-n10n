package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.gu.liveactivities.models.LiveActivityData
import com.gu.liveactivities.service.ChannelApiClient
import com.gu.liveactivities.util.Logging
import com.gu.mobile.notifications.client.models.liveActitivites._
import play.api.libs.json.{Format, JsError, JsSuccess, Json}

import java.io.{InputStream, OutputStream}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

case class ChannelRequest(matchId: String, competitionId: Option[String], eventData: Option[LiveActivityData], toCreate: Boolean)

object ChannelRequest {
  implicit val jf: Format[ChannelRequest] = Json.format[ChannelRequest]
}

object ChannelManagerLambda extends RequestStreamHandler with Lambda with Logging {

  val channelApiClient = new ChannelApiClient(authentication, config.bundleId, config.sendingToProdServer)

  def processCreateChannelRequest(matchId: String, eventData: Option[LiveActivityData]): Future[String] = {
    logger.info(s"Received request to create channel for match ID $matchId")

    val maybeChannelId = repository.containMapping(matchId)
    maybeChannelId.flatMap {
      case Some(id) =>
        logger.error(s"Channel mapping already exists for match ID $matchId")
        // if channel id already exists for a match we don't want to fail the lambda to avoid the lambda retry on this event
        Future.successful(id)
      case None =>
        for {
          channelId <- channelApiClient.createChannel()
          _ <- repository.createMapping(matchId, channelId, eventData)
          _ = logger.info(s"Channel created with channel ID $channelId for match ID $matchId")
        } yield channelId
    }
  }

  def processCloseChannelRequest(matchId: String): Future[String] = {
    logger.info(s"Channel closed for match ID: $matchId")
    val maybeMapping = repository.getMappingById(matchId)
    maybeMapping.flatMap{
      case mapping if !mapping.isChannelActive =>
        logger.error(s"Channel not active for match ID $matchId")
        // if channel is inactive for a match we don't want to fail the lambda to avoid the lambda retry on this event
        Future.successful(mapping.channelId)
      case mapping =>  for {
        _ <- channelApiClient.closeChannel(mapping.channelId)
        _ <- repository.updateMappingActiveChannel(matchId, isActive = false)
        _ = logger.info(s"Channel closed with channel ID ${mapping.channelId} for match ID $matchId")
      } yield mapping.channelId
    }
  }

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[EventBridgeEvent] match {
      case JsSuccess(request, _) => {
        request.`detail-type` match {
          case ChannelCreate | ChannelDelete =>
            logger.info(s"Received ${request.`detail-type`.asString} event for event ID ${request.id}")
          case other =>
            logger.warn(s"Received unsupported event type: ${other.asString} for event ID ${request.id}")
        }
        processRequest(request.detail, context)
      }
      case JsError(errors) => {
        logger.error(s"Failed to parse request: $errors")
        throw new Exception(s"Invalid request: $errors")
      }
    }
  }

  def processRequest(request: LiveActivityPayload, context: Context): Unit = {
    val channelFuture = request.eventType match {
      case CreateChannelEvent =>
        val eventData = request.broadcastContentStateData.map(LiveActivityData.toLiveActivityData)
        processCreateChannelRequest(request.liveActivityID, eventData)
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
        logger.error(s"Failed to process: ${exception.getMessage}")
        throw exception
    }
  }
}
