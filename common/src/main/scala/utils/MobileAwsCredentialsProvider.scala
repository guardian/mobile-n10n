package utils

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain => AwsCredentialsProviderChainV2,
  ProfileCredentialsProvider => ProfileCredentialsProviderV2,
  DefaultCredentialsProvider => DefaultCredentialsProviderV2
}

class MobileAwsCredentialsProvider extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("mobile"),
  DefaultAWSCredentialsProviderChain.getInstance
)

object MobileAwsCredentialsProvider {
  val mobileAwsCredentialsProviderv2 = AwsCredentialsProviderChainV2.of(
    ProfileCredentialsProviderV2.builder.profileName("mobile").build,
    DefaultCredentialsProviderV2.builder().build()
  )
}
