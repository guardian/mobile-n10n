package utils

import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  ProfileCredentialsProvider,
  DefaultCredentialsProvider
}

object MobileAwsCredentialsProvider {
  val mobileAwsCredentialsProvider = AwsCredentialsProviderChain.of(
    ProfileCredentialsProvider.builder.profileName("mobile").build,
    DefaultCredentialsProvider.builder().build()
  )
}
