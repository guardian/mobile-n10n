package utils

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}

class MobileAwsCredentialsProvider extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("mobile"),
  DefaultAWSCredentialsProviderChain.getInstance
)
