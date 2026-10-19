package com.gu.notifications.worker.utils

import utils.MobileAwsCredentialsProvider

object Aws {
  lazy val credentialsProvider = new MobileAwsCredentialsProvider
}
