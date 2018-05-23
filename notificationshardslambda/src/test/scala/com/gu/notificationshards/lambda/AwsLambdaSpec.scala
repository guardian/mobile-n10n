package com.gu.notificationshards.lambda

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.nio.charset.StandardCharsets

import com.gu.notificationshards.cloudwatch.CloudWatchPublisher
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class AwsLambdaSpec extends Specification with Mockito {
  "AwsLambda" should {
    "send metrics" in {
      val input: InputStream = new ByteArrayInputStream("".getBytes)
      val output = new ByteArrayOutputStream()
      val cloudWatchPublisher = mock[CloudWatchPublisher]
      new AwsLambda(x => x, cloudWatch = cloudWatchPublisher) {}.handleRequest(input, output, null)
      there was one(cloudWatchPublisher).sendMetricsSoFar()
    }
    "apply function" in {
      val input: InputStream = new ByteArrayInputStream("test-input".getBytes)
      val output = new ByteArrayOutputStream()
      val cloudWatchPublisher = mock[CloudWatchPublisher]
      new AwsLambda(input => {
        input must be_==("test-input")
        "test-output"
      }, cloudWatch = cloudWatchPublisher) {}.handleRequest(input, output, null)
      new String(output.toByteArray, StandardCharsets.UTF_8) must be_==("test-output")
    }
  }

}
