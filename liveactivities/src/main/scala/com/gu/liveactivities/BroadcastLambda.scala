package com.gu.liveactivities

import com.gu.liveactivities.service.BroadcastApiClient
import com.gu.liveactivities.util.{Configuration, IosConfiguration}
import scala.concurrent.Await
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import java.io.{InputStream, OutputStream}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.functional.syntax._
import java.nio.charset.StandardCharsets
import com.gu.liveactivities.service.Authentication
import com.gu.liveactivities.util.Logging
import scala.concurrent.Future
import com.gu.liveactivities.models.LiveActivityInvalidStateException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.gu.liveactivities.util.DateTimeHelper.{dateTimeToString, dateTimeFromString}

// TODO - we should get the channel ID by looking up the match ID in the datastore
case class BroadcastRequest(
    matchId: String, 
    payload: String, 
    eventId: Option[String], 
    eventTime: Option[ZonedDateTime])

object BroadcastRequest {
	implicit val jf: Format[BroadcastRequest] = Json.format[BroadcastRequest]

  implicit val reads: Reads[BroadcastRequest] = (
    (JsPath \ "matchId").read[String] and
    (JsPath \ "payload").read[String] and
    (JsPath \ "eventId").readNullable[String] and
    (JsPath \ "eventTime").readNullable[String].map(_.map(dateTimeFromString))
  )(BroadcastRequest.apply _)

  implicit val writes: OWrites[BroadcastRequest] = (
    (JsPath \ "matchId").write[String] and
    (JsPath \ "payload").write[String] and
    (JsPath \ "eventId").writeNullable[String] and
    (JsPath \ "eventTime").writeNullable[String]
  )(r => (r.matchId, r.payload, r.eventId, r.eventTime.map(dateTimeToString)))
}

object BroadcastLambda extends RequestStreamHandler with Lambda with Logging {

  val broadcastApiClient = new BroadcastApiClient(authentication, config.bundleId, config.sendingToProdServer)

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[BroadcastRequest] match {
      case JsSuccess(request, _) => {
        processRequest(request, context)
      }
      case JsError(errors) => {
        logger.error(s"Failed to parse request: $errors")
        throw new Exception(s"Invalid request: $errors")
      }
    }
  }

  def processRequest(request: BroadcastRequest, context: Context): Unit = {
    logger.info(s"Received request to create channel for match ID ${request.matchId}")
    val broadcastFuture = for {
      mapping <- repository.getMappingById(request.matchId)
      _ <- if (!mapping.isChannelActive) {
          logger.error(s"Channel not active for match ID ${request.matchId}")
          Future.failed(new LiveActivityInvalidStateException(request.matchId, "Channel not active"))
        } else Future.successful(())
      _ <- if (!mapping.isEventLive) {
          logger.error(s"Event not live for match ID ${request.matchId}")
          Future.failed(new LiveActivityInvalidStateException(request.matchId, "Event not live"))
        } else Future.successful(())
      _ <- if (request.eventId.exists(id => mapping.lastEventId.contains(id))) {
          logger.warn(s"Duplicate event ID ${request.eventId.getOrElse("")} for match ID ${request.matchId}")
          Future.failed(new LiveActivityInvalidStateException(request.matchId, "Duplicate event ID"))
        } else Future.successful(())
      _ <- if (request.eventTime.exists(eventTime => mapping.lastEventAt.exists(lastEventAt => eventTime.isBefore(lastEventAt)))) {
          logger.warn(s"Out of order event time ${request.eventTime.map(dateTimeToString).getOrElse("")} for match ID ${request.matchId}")
          Future.failed(new LiveActivityInvalidStateException(request.matchId, "Out of order event time"))
        } else Future.successful(())
      // TODO - determine expiry time and priority
      _ <- broadcastApiClient.sendToChannel(mapping.channelId, None, None)
      _ = logger.info(s"Broadcast sent successfully for match ID ${request.matchId} with channel ID ${mapping.channelId}")
    } yield mapping.channelId

    // TODO - the timeout value
    Try(Await.result(broadcastFuture, scala.concurrent.duration.Duration.Inf)) match {
      case Success(apnResponse) => ()
      case Failure(exception) => {
        logger.error(s"Failed to send broadcast: ${exception.getMessage}")
        throw exception
      }
    }
  }
}
