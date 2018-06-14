package utils

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, EC2ContainerCredentialsProviderWrapper}

class MobileAwsCredentialsProvider extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("mobile"),
  new EC2ContainerCredentialsProviderWrapper()
)
