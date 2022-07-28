package com.gu.notifications.workerlambda.utils

import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain => AwsCredentialsProviderChainV2,
  ProfileCredentialsProvider => ProfileCredentialsProviderV2,
  DefaultCredentialsProvider => DefaultCredentialsProviderV2
}

object MobileAwsCredentialsProvider {
  val mobileAwsCredentialsProviderv2 = AwsCredentialsProviderChainV2.of(
    ProfileCredentialsProviderV2.builder.profileName("mobile").build,
    DefaultCredentialsProviderV2.create
  )
}
