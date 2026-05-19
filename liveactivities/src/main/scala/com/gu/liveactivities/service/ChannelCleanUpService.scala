package com.gu.liveactivities.service

import com.gu.liveactivities.models.{LiveActivityMapping, RepositoryException}
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
      // todo repo failure will throw
      channelsForPossibleDeletion <- repository.fetchAllMappingsByStatus(
        isChannelActive = true,
        isLive = false,
        hasLastEvent = true,
      )
      _ = logger.info(s"Channels identified for possible deletion: ${channelsForPossibleDeletion.size}")
      // todo this sequence failures will not currently throw
      _ <- Future.sequence(
        channelsForPossibleDeletion.map { mapping =>
        {
          if (mapping.createdAt.isAfter(ZonedDateTime.now().minusHours(24))) {
            logger.info(s"Channel with ID ${mapping.channelId} for match ID ${mapping.id} is " +
              s"not live but was created less than 24hrs ago, skipping deletion.")
            Future.successful(())
          } else {
            channelApiClient
              .closeChannel(mapping.channelId)
              .flatMap { _ =>
                val f = repository.updateMappingActiveChannel(mapping.id, isActive = false)
                  .map { result =>
                    channelsDeleted ++= List(mapping)
                    logger.info(s"Channel successfully marked inactive with channel ID: ${mapping.channelId} for match ID ${mapping.id}")
                    result
                  }
                f
              }
              .recover { case exception =>
                // todo handle exception better and throw when needed. Some errors will be handled by rest of the Cleanup tasks.
                logger.error(s"Failed to delete channel with ID ${mapping.channelId} for match ID ${mapping.id} - ${exception.getMessage}")
              }


          }
        }
        },
      )
    } yield channelsDeleted.toList
  }


//  // todo not running yet
//  // No channel should exist in APNS without an active mapping in Dynamo
//  private def deleteOrphanChannelsInAPNS(): Future[List[String]] = {
//    for {
//      allChannels <- channelApiClient.getAllChannels()
//      allMappings <- repository.fetchAllMappings()
//      allActiveMappings = allMappings.filter(_.isChannelActive)
//      orphanChannels = allChannels.filter(c => !allActiveMappings.exists(_.channelId == c))
//      _ <- Future.sequence(
//        orphanChannels.map { channelId =>
//          channelApiClient
//            .closeChannel(channelId)
//            .recover { case exception =>
//              logger.error(s"Failed to delete orphan channel with ID ${channelId} - ${exception.getMessage}")
//            }
//        },
//      )
//    } yield orphanChannels
//  }
}
