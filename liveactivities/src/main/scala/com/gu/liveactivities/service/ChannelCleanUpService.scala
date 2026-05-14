package com.gu.liveactivities.service

import com.gu.liveactivities.models.LiveActivityMapping
import com.gu.liveactivities.util.Logging

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

class ChannelCleanUpService(
                             repository: ChannelMappingsRepository,
                             channelApiClient: ChannelApiClient,
                           )(implicit ec: ExecutionContext) extends Logging {

  def deleteChannelsForEndedBroadcasts(): Future[List[LiveActivityMapping]] = {

    val channelsDeleted = scala.collection.mutable.ListBuffer.empty[LiveActivityMapping]

    for {
      channelsForPossibleDeletion <- repository.fetchAllMappingsByStatus(
        isChannelActive = true,
        isLive = false,
        hasLastEvent = true,
      )
      _ = logger.info(s"Channels identified for possible deletion: ${channelsForPossibleDeletion.size}")
      _ <- Future.sequence(
        channelsForPossibleDeletion.map { mapping =>
        {
          if (mapping.createdAt.isAfter(ZonedDateTime.now().minusHours(48))) { // TODO TEST
            logger.info(s"Channel with ID ${mapping.channelId} for match ID ${mapping.id} is " +
              s"not live but was created less than 24hrs ago, skipping deletion.")
            Future.successful(())
          } else {
            channelApiClient
              .closeChannel(mapping.channelId)
              .flatMap { _ =>
                val f = repository.updateMappingActiveChannel(mapping.id, isActive = false)
                channelsDeleted ++= List(mapping)
                logger.info(s"Channel successfully marked inactive with channel ID: ${mapping.channelId} for match ID ${mapping.id}")
                f
              }
              .recover { case exception =>
                // todo we should alert on this failure
                logger.error(s"Failed to delete channel with ID ${mapping.channelId} for match ID ${mapping.id} - ${exception.getMessage}")
              }
          }
        }
        },
      )
    } yield channelsDeleted.toList

  }

  // todo not running yet
  // No channel should exist in APNS without an active mapping in Dynamo
  private def deleteOrphanChannelsInAPNS(): Future[List[String]] = {
    for {
      allChannels <- channelApiClient.getAllChannels()
      allMappings <- repository.fetchAllMappings()
      allActiveMappings = allMappings.filter(_.isChannelActive)
      orphanChannels = allChannels.filter(c => !allActiveMappings.exists(_.channelId == c))
      _ <- Future.sequence(
        orphanChannels.map { channelId =>
          channelApiClient
            .closeChannel(channelId)
            .recover { case exception =>
              logger.error(s"Failed to delete orphan channel with ID ${channelId} - ${exception.getMessage}")
            }
        },
      )
    } yield orphanChannels
  }
}
