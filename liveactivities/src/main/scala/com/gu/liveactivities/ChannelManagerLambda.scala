package com.gu.liveactivities

import com.gu.liveactivities.service.{ChannelApiClient, BroadcastApiClient, Authentication}
import com.gu.liveactivities.service.LiveActivityChannelRepository
import com.gu.liveactivities.util.{Configuration, IosConfiguration}
import com.gu.liveactivities.models.{LiveActivityMapping, LiveActivityData}
import scala.concurrent.Await
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain => AwsCredentialsProviderChainV2,
  ProfileCredentialsProvider => ProfileCredentialsProviderV2,
  DefaultCredentialsProvider => DefaultCredentialsProviderV2,
}
import java.io.{InputStream, OutputStream}
import play.api.libs.json.Json
import play.api.libs.json.Format
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import java.nio.charset.StandardCharsets
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.regions.Region.EU_WEST_1
import com.gu.liveactivities.util.Logging
import scala.concurrent.Future
import com.gu.liveactivities.models.LiveActivityInvalidStateException

// TODO - we should get the channel ID by looking up the match ID in the datastore
case class ChannelRequest(matchId: String, channelId: String, toCreate: Boolean)

object ChannelRequest {
  implicit val jf: Format[ChannelRequest] = Json.format[ChannelRequest]
}

object ChannelManagerLambda extends RequestStreamHandler with Logging {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val config: IosConfiguration = Configuration.fetchIos()

  val authentication = new Authentication(config.teamId, config.keyId, config.certificate)

  val channelApiClient = new ChannelApiClient(authentication, config.bundleId, config.sendingToProdServer)

  val credentialsv2 = AwsCredentialsProviderChainV2.of(
    ProfileCredentialsProviderV2.builder.profileName("mobile").build,
    DefaultCredentialsProviderV2.builder.build(),
  )

  val dynamoDbClient =
    DynamoDbAsyncClient
      .builder()
      .credentialsProvider(credentialsv2)
      .region(EU_WEST_1)
      .build()

  val repository = new service.LiveActivityChannelRepository(dynamoDbClient, "LiveActivityChannels")

  def processCreateChannelRequest(matchId: String, eventData: Option[LiveActivityData], competitionId: Option[String]): Future[String] = {
    logger.info(s"Received request to create channel for match ID ${matchId}")
    return for {
      mappingExists <- repository.containMapping(matchId)
      _ <- if (mappingExists) {
          logger.error(s"Channel mapping already exists for match ID ${matchId}")
          Future.failed(new LiveActivityInvalidStateException(matchId, "Channel mapping already exists"))
        } else Future.successful(())
      channelId <- channelApiClient.createChannel()
      _ <- repository.createMapping(matchId, channelId, eventData, competitionId)
      _ = logger.info(s"Channel created with channel ID ${channelId} for match ID ${matchId}")
    } yield channelId
  }

  def processCloseChannelRequest(matchId: String): Future[String] = {
    logger.info(s"Channel closed for match ID: $matchId")
    return for {
      mapping <- repository.getMappingById(matchId)
      _ <- if (!mapping.isChannelActive) {
          logger.error(s"Channel not active for match ID ${matchId}")
	        Future.failed(new LiveActivityInvalidStateException(matchId, "Channel not active"))
        } else Future.successful(())
      _ <- channelApiClient.closeChannel(mapping.channelId)
      _ <- repository.updateMappingActiveChannel(matchId, false)
      _ = logger.info(s"Channel closed with channel ID ${mapping.channelId} for match ID ${matchId}")
    } yield mapping.channelId
  }

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    Json.parse(input).validate[ChannelRequest] match {
      case JsSuccess(request, _) => {
        processRequest(request, context)
      }
      case JsError(errors) => {
        throw new Exception(s"Invalid request: $errors")
      }
    }
  }

  def processRequest(request: ChannelRequest, context: Context): Unit = {
    val channelFuture = 
      if (request.toCreate)
        processCreateChannelRequest(request.matchId, None, None)
      else
        processCloseChannelRequest(request.matchId)

    // TODO - the timeout value
    Try(Await.result(channelFuture, scala.concurrent.duration.Duration.Inf)) match {
      case Success(_) => ()
      case Failure(exception) => {
        logger.error(s"Failed to process: ${exception.getMessage}")
        throw exception
      }
    }
  }
}
