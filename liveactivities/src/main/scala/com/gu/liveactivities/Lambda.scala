package com.gu.liveactivities

import com.gu.liveactivities.service.{Authentication, LiveActivityChannelRepository}
import com.gu.liveactivities.util.{Configuration, IosConfiguration}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.DefaultRetryStrategy
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import java.time.Duration

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
      .overrideConfiguration(
        ClientOverrideConfiguration.builder()
          .apiCallAttemptTimeout(Duration.ofSeconds(2)) // limit for one single try
          .apiCallTimeout(Duration.ofSeconds(10)) // limit for the entire operation
          .retryStrategy(DefaultRetryStrategy.standardStrategyBuilder()
            .maxAttempts(3)
            .build())
          .build()
      )
      .build()

  val repository = new LiveActivityChannelRepository(dynamoDbClient, s"mobile-notifications-liveactivities-${config.stage}")
}
