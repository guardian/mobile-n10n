package utils

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain, EC2ContainerCredentialsProviderWrapper}

class MobileAwsCredentialsProvider extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("mobile"),
  DefaultAWSCredentialsProviderChain.getInstance
)
