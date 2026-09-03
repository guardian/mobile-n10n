package com.gu.notifications.worker.utils

import utils.MobileAwsCredentialsProvider

object Aws {
  lazy val credentialsProviderV2 = MobileAwsCredentialsProvider.mobileAwsCredentialsProviderv2
}
