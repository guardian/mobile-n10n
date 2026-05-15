package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.gu.liveactivities.service.{BroadcastApiClient, BroadcastService}
import com.gu.liveactivities.util.Logging
import com.gu.mobile.notifications.client.models.liveActitivites._
import play.api.libs.json._

import java.io.{InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}



object BroadcastLambda extends RequestStreamHandler with Lambda with Logging {

  val broadcastApiClient = new BroadcastApiClient(authentication, config.bundleId, config.sendingToProdServer)
  val broadcastService = new BroadcastService(repository, broadcastApiClient)

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[EventBridgeEvent] match {
      case JsSuccess(request, _) => {

        // If we see these errors there is a misconfiguration in the eventbridge routing rules.
        if (request.`detail-type` != UpdateLiveActivityEvent &&
          request.`detail-type` != EndLiveActivityEvent &&
          request.`detail-type` != StartLiveActivityEvent
        ) {
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

    val contentState: FootballMatchContentState = requestPayload.broadcastContentStateData match {
      case Some(cs: FootballMatchContentState) => cs
      case Some(other) =>
        throw new Exception(s"Unexpected content state type: ${other.getClass.getSimpleName}")
      case None =>
        throw new Exception(s"Missing content state for match ID $matchId")
    }

    val shouldEndBroadcast: Boolean = requestPayload.eventType match {
      case EndLiveActivityEvent => true
      case StartLiveActivityEvent => false
      case UpdateLiveActivityEvent => false
      case _ =>
        logger.error(s"Unexpected event type ${requestPayload.eventType} for broadcast payload")
        throw new Exception(s"Unexpected event type ${requestPayload.eventType} for broadcast payload")
    }

    val broadcastFuture = broadcastService.processBroadcast(requestPayload, shouldEndBroadcast, contentState)

    //Timeout set to 160 seconds to provide a safety buffer.
    // While the sum of downstream timeouts is at most ~110s,
    // we allow extra time for other things such as JSON parsing, cold starts,
    // authentication token fetches, and potential network congestion
    // to ensure the Lambda doesn't fail a healthy request that is just running slow.
    Try(Await.result(broadcastFuture, 160.seconds)) match {
      case Success(_) => {
        // todo
        logger.info(s"Broadcast ${requestPayload.eventType.asString} successfully processed for liveActivityID $matchId")
      }
      case Failure(exception) => {
        logger.error(s"Failed to send broadcast ${if(shouldEndBroadcast)"END"} for liveActivityID $matchId: ${exception.getMessage}")
        throw exception
      }
    }
  }
}
