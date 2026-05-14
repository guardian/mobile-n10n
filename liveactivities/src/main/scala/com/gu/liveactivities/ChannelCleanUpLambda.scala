package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.gu.liveactivities.models.LiveActivityMapping
import com.gu.liveactivities.service.{ChannelApiClient, ChannelMappingsRepository}
import com.gu.liveactivities.util.Logging

import java.time.ZonedDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ChannelCleanUpService(
    repository: ChannelMappingsRepository,
    channelApiClient: ChannelApiClient,
)(implicit ec: ExecutionContext) extends Logging {

  def deleteChannelsForEndedBroadcasts(): Future[List[LiveActivityMapping]] = {
    for {
      channelsForDeletion <- repository.fetchAllMappingsByStatus(
        isChannelActive = true,
        isLive = false,
        hasLastEvent = true,
      )
      _ = logger.info(s"Channels identified for possible deletion: ${channelsForDeletion.size}")
      _ <- Future.sequence(
        channelsForDeletion.map { mapping =>
          {
            if (mapping.createdAt.isAfter(ZonedDateTime.now().minusHours(48))) { // TODO TEST
              logger.warn(s"Channel with ID ${mapping.channelId} for match ID ${mapping.id} is " +
                  s"not live but was created less than 24hrs ago, skipping deletion.")
              Future.successful(())
            } else {
              channelApiClient
                .closeChannel(mapping.channelId)
                .flatMap { _ =>
                  val f = repository.updateMappingActiveChannel(mapping.id, isActive = false)
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
    } yield channelsForDeletion
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

object ChannelCleanUpLambda extends RequestHandler[ScheduledEvent, Unit] with Lambda with Logging {

  private val channelApiClient = new ChannelApiClient(authentication, config.bundleId, config.sendingToProdServer)
  private val cleanUpService = new ChannelCleanUpService(repository, channelApiClient)

  private def cleanUpChannels(): Future[Unit] = {
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
      case Success(_)         => Future.successful(())
      case Failure(exception) =>
        logger.error(s"Failed to process: ${exception.getMessage}")
        throw exception
    }
  }

  def handleRequest(input: ScheduledEvent, context: Context): Unit = {
    logger.info("Channel clean up lambda triggered")
    // todo assess timeout
    Try(Await.result(cleanUpChannels(), 160.seconds)) match {
      case Success(_)         => logger.info("Channel clean up completed successfully")
      case Failure(exception) =>
        logger.error(s"Channel clean up failed: ${exception.getMessage}")
        throw exception
    }
  }
}
