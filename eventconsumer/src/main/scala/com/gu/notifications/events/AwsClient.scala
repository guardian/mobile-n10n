package com.gu.notifications.events

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}

object AwsClient {
  lazy val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance)

  lazy val s3Client: AmazonS3 = AmazonS3Client.builder()
    .withCredentials(credentials)
    .withRegion("eu-west-1")
    .build
}
