package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.gu.liveactivities.service.{ChannelApiClient, ChannelCleanUpService}
import com.gu.liveactivities.util.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

object ChannelCleanUpLambda extends Lambda with Logging {

  private val channelApiClient = new ChannelApiClient(authentication, config.bundleId, config.sendingToProdServer)
  private val cleanUpService = new ChannelCleanUpService(repository, channelApiClient)

  // we need a scheduledEvent input here so we have some metadata to capture in the DLQ if there is a failure.
  def handleRequest(input: ScheduledEvent, context: Context): Unit = {
    logger.info("Channel clean up lambda triggered")

    val future = for {
      deletedChannels <- cleanUpService.deleteChannelsForEndedBroadcasts()
      // orphanChannels <- deleteOrphanChannelsInAPNS() // channels that exist in APNS but corresponding mapping is missing in Dynamo
      // staleMappings <- markInactiveStaleMappings // mapping still "LIVE" that missed an end activity update and need to be deleted - Mapi may return channel id otherwise
      // orphanMappings <- markInactiveOrphanMappings // mapping still "active" but channel has been deleted.
      cleanupSummary = {
        deletedChannels
//        orphanChannels,
//        staleMappings,
//        orphanMappings,
      }
    } yield cleanupSummary

    Try(Await.result(future, 160.seconds)) match {
      case Success(_) => {
        logger.info("Channel clean up completed successfully")
      }
      case Failure(exception) =>
        logger.error(s"Channel clean up failed: ${exception.getMessage}")
        throw exception
    }
  }

}
