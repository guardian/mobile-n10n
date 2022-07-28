package com.gu.notifications.workerlambda.utils
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, EnvironmentVariableCredentialsProvider, ProfileCredentialsProvider, SystemPropertyCredentialsProvider}

object Aws {
  lazy val CredentialsProvider: AwsCredentialsProviderChain = AwsCredentialsProviderChain
    .builder
    .credentialsProviders(
      EnvironmentVariableCredentialsProvider.create(),
      SystemPropertyCredentialsProvider.create(),
      ProfileCredentialsProvider.builder.profileName("mobile").build()
    )
    .build()
}
