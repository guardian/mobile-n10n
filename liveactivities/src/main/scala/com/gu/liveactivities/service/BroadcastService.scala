package com.gu.liveactivities.service

import com.gu.liveactivities.models.{BroadcastBody, LiveActivityInvalidStateException}
import com.gu.liveactivities.util.DateTimeHelper.{dateTimeFromLong, dateTimeToString}
import com.gu.liveactivities.util.Logging
import com.gu.mobile.notifications.client.models.liveActitivites.{ContentState, EndLiveActivityEvent, FootballLiveActivity, LiveActivityPayload}

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

class BroadcastService(repository: ChannelMappingsRepository, broadcastApiClient: BroadcastApiClient)(implicit ec: ExecutionContext) extends Logging {

  def processBroadcast(requestPayload: LiveActivityPayload, shouldEndBroadcast: Boolean, contentState: ContentState): Future[String] = {
    val matchId = requestPayload.liveActivityID
    val eventId: String = requestPayload.id.toString
    val eventTime: ZonedDateTime = dateTimeFromLong(requestPayload.eventTimestamp) // time triggering event was received and processed in football lambda
    val broadcastFuture = repository.getMappingById(matchId).flatMap { mapping =>
      // If the mapping is not live, but it HAS a lastEventId, it means it was Live once and has now Ended.
      // We want to skip any further updates in this terminal state.
      // But we don't want to fail the lambda because it's expected that the source may continue
      // to send update events after a session has ended. Therefore, we treat these as expected no-ops.
      val broadcastNotAllowed = !shouldEndBroadcast && !mapping.isLive && mapping.lastEventId.isDefined
      val priorityLevel = if (requestPayload.liveActivityType == FootballLiveActivity) Some(10) else None

      if (broadcastNotAllowed) {
        logger.warn(s"${requestPayload.eventType.asString} event ID $eventId not allowed after ${EndLiveActivityEvent.asString} for match ID $matchId")
        Future.successful(mapping.channelId)
      } else {
        for {
          _ <- if (!mapping.isChannelActive) {
            logger.error(s"Channel not active for match ID $matchId")
            Future.failed(new LiveActivityInvalidStateException(matchId, "Channel not active"))
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
          _ <- broadcastApiClient.sendToChannel(mapping.channelId, None, priorityLevel, broadcastPayload)
          _ = logger.info(s"Broadcast ${requestPayload.eventType.asString} sent successfully for match ID $matchId to channel ID ${mapping.channelId}")

          _ <- repository.updateMappingLiveAndLastEvent(matchId, isLive = !shouldEndBroadcast, Some(eventId), Some(eventTime))
          _ = logger.info(s"Record updated successfully for match ID $matchId")
        } yield mapping.channelId
      }
    }

    broadcastFuture
  }
}
