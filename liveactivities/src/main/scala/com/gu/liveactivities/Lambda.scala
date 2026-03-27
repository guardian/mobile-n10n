package com.gu.liveactivities

import com.gu.liveactivities.service.Authentication
import com.gu.liveactivities.service.ChannelApiClient
import com.gu.liveactivities.service.LiveActivityChannelRepository
import com.gu.liveactivities.util.Configuration
import com.gu.liveactivities.util.IosConfiguration
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.auth.credentials.{
	AwsCredentialsProviderChain,
	ProfileCredentialsProvider,
	DefaultCredentialsProvider,
}
import software.amazon.awssdk.regions.Region

trait Lambda {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val config: IosConfiguration = Configuration.fetchIos()

  val authentication = new Authentication(config.teamId, config.keyId, config.certificate)

  val credentialsv2 = AwsCredentialsProviderChain.of(
    ProfileCredentialsProvider.builder.profileName("mobile").build,
    DefaultCredentialsProvider.builder.build(),
  )

  val dynamoDbClient =
    DynamoDbAsyncClient
      .builder()
      .credentialsProvider(credentialsv2)
      .region(Region.of(config.region))
      .build()

  val repository = new LiveActivityChannelRepository(dynamoDbClient, s"mobile-notifications-liveactivities-${config.stage}")
}
