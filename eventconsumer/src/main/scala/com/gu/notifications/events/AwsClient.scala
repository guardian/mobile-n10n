package com.gu.notifications.events

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}

object AwsClient {
  lazy val credentials: AWSCredentialsProviderChain = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance)


  lazy val s3Client: AmazonS3 = AmazonS3Client.builder()
    .withCredentials(credentials)
    .withRegion(Regions.EU_WEST_1)
    .build

  lazy val dynamoDbClient: AmazonDynamoDBAsync = AmazonDynamoDBAsyncClientBuilder.standard()
    .withCredentials(credentials)
    .withRegion(Regions.EU_WEST_1)
    .build()
}
