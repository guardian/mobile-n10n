package com.gu.liveactivities

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.gu.liveactivities.service.{ChannelApiClient}
import com.gu.liveactivities.util.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/** This lambda is scheduled to run once a day to delete isLive=False channels from APNS
  * and mark them as inactive in the Dynamo. We also mark as inactive/not live any channels
  * created more than 24hrs ago in case there was an error delivering the End Activity update.
  *
  * It also deletes any orphan channels in APNS that do not have a corresponding mapping in Dynamo - this
  * should be rare but could happen if there were network issues during channel creation. Inactive channel
  * mappings are retained in Dynamo for 14 days then deleted via Dynamo TTL.
  *
  * The lambda will also mark as inactive any mapping in Dynamo that does not have a corresponding channel
  * in APNS - this could happen if there were network issues during channel deletion.
  */
object ChannelCleanUpLambda
    extends RequestHandler[ScheduledEvent, Unit]
    with Lambda
    with Logging {

  private val channelApiClient = new ChannelApiClient(
    authentication,
    config.bundleId,
    config.sendingToProdServer
  )

  def handleRequest(input: ScheduledEvent, context: Context): Unit = {
    logger.info("Channel clean up lambda triggered")

    Try(Await.result(cleanUpChannels(), 160.seconds)) match {
      case Success(_) => logger.info("Channel clean up completed successfully")
      case Failure(exception) =>
        logger.error(s"Channel clean up failed: ${exception.getMessage}")
        throw exception
    }
  }

  private def cleanUpChannels(): Future[Unit] = {
    val future = for {
      allChannels <- channelApiClient.getAllChannels()
    } yield allChannels

    Try(Await.result(future, 160.seconds)) match {
      case Success(future) => {
        Future.successful(())
      }
      case Failure(exception) =>
        logger.error(s"Failed to process: ${exception.getMessage}")
        throw exception
    }
  }

}
