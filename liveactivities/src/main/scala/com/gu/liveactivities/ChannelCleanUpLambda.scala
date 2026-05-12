package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.gu.liveactivities.models.LiveActivityMapping
import com.gu.liveactivities.service.ChannelApiClient
import com.gu.liveactivities.util.Logging

import java.time.ZonedDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/** This lambda is scheduled to run once a day:
  *
  * 1/ Delete isLive=False channels from APNS and marks them as inactive in the Dynamo for future expiration by ttl.
  *
  * TBC?? Mark as not live any channels created more than 72hrs ago in case there was an error sending the End Activity
  * update.
  *
  * 2/ Deletes any orphan channels in APNS that do not have a corresponding mapping in Dynamo - this should be rare but
  * could happen if there were network issues during channel creation. Inactive channel mappings are retained in Dynamo
  * for 14 days then deleted via Dynamo TTL.
  *
 *  TBD
  * 3/ The lambda will also mark as inactive any mapping in Dynamo that does not have a corresponding channel in APNS -
  * this could happen if there were network issues during channel deletion.
  */
object ChannelCleanUpLambda extends RequestHandler[ScheduledEvent, Unit] with Lambda with Logging {

  private val channelApiClient = new ChannelApiClient(authentication, config.bundleId, config.sendingToProdServer)

  private def deleteChannelsForEndedBroadcasts(): Future[List[LiveActivityMapping]] = {
    for {
      channelsForDeletion <- repository.fetchAllMappingsByStatus(
        isChannelActive = true,
        isLive = false,
        hasLastEvent = true,
      )
      _ <- Future.sequence(
        channelsForDeletion.map { mapping =>
          {
            if (mapping.createdAt.isAfter(ZonedDateTime.now().minusHours(72))) { // TODO
              logger.warn(
                s"Channel with ID ${mapping.channelId} for match ID ${mapping.id} is " +
                  s"not live but was created less than 24hrs ago, skipping deletion.",
              )
            }
            channelApiClient
              .closeChannel(mapping.channelId)
              .flatMap { _ =>
                repository.updateMappingActiveChannel(mapping.id, isActive = false)
              } // ttl will clear these from dynamo eventually
              .recover { case exception =>
                // todo we should alert on this failure
                logger.error(
                  s"Failed to delete channel with ID ${mapping.channelId} for match ID ${mapping.id} - ${exception.getMessage}",
                )
              }
          }
        },
      )
    } yield channelsForDeletion
  }

  // No channel should exist in APNS without an active mapping in Dynamo
  private def deleteOrphanChannelsInAPNS(): Future[List[String]] = {
    for {
      allChannels <- channelApiClient.getAllChannels()
      // todo de we want to wait in case the channel mapping was being created?
      allMappings <- repository.fetchAllMappings()
      allActiveMappings = allMappings.filter(_.isChannelActive)
      orphanChannels = allChannels.filter(c => !allActiveMappings.exists(_.channelId == c))
      _ <- Future.sequence(
        orphanChannels.map { channelId =>
          channelApiClient.closeChannel(channelId)
            .recover { case exception =>
              // todo we should alert on this failure
              logger.error(
                s"Failed to delete orphan channel with ID ${channelId} - ${exception.getMessage}",
              )
            }
        },
      )
    } yield orphanChannels
  }

  // todo should we run some sort of quantity checksum?
  private def cleanUpChannels(): Future[Unit] = {
    val future = for {
      deletedChannels <- deleteChannelsForEndedBroadcasts()
      // todo clean up orphan apns channels without mappings
      // todo clean up stale mappings that missed an end activity update and need to be deleted.
      // todo mark as inactive any channels that have no corresponding channel in APNS
      orphanChannels <- deleteOrphanChannelsInAPNS() // todo maybe we don't run this initially.
    } yield (deletedChannels.size, orphanChannels.size)

    Try(Await.result(future, 160.seconds)) match {
      case Success(_)         => Future.successful(())
      case Failure(exception) =>
        logger.error(s"Failed to process: ${exception.getMessage}")
        throw exception
    }
  }

  def handleRequest(input: ScheduledEvent, context: Context): Unit = {
    logger.info("Channel clean up lambda triggered")

    Try(Await.result(cleanUpChannels(), 160.seconds)) match {
      case Success(_)         => logger.info("Channel clean up completed successfully")
      case Failure(exception) =>
        logger.error(s"Channel clean up failed: ${exception.getMessage}")
        throw exception
    }
  }
}
