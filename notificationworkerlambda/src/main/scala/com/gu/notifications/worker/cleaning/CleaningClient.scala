package com.gu.notifications.worker.cleaning

import cats.effect.IO
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClient}
import com.gu.notifications.worker.delivery.DeliveryClient
import com.gu.notifications.worker.models.{InvalidTokens, SendingResults}
import fs2.Pipe
import org.slf4j.Logger
import play.api.libs.json.Json
import utils.MobileAwsCredentialsProvider

class CleaningClient(sqsUrl: String) {
  val credentialsProvider = new MobileAwsCredentialsProvider

  val sqsClient: AmazonSQS = AmazonSQSClient
    .builder()
    .withCredentials(credentialsProvider)
    .withRegion("eu-west-1")
    .build


  def sendInvalidTokensToCleaning[C <: DeliveryClient](implicit logger: Logger): Pipe[IO, SendingResults, SendingResults] =
    _.evalMap { sendingResults =>
      IO.delay {
        val invalidTokens = sendingResults.invalidTokens.tokens
        val batches = invalidTokens.grouped(1000).map(InvalidTokens.apply)
        batches.foreach { batch =>
          val json = Json.stringify(Json.toJson(batch))
          sqsClient.sendMessage(sqsUrl, json)
        }
        logger.info(s"Sent ${invalidTokens.size} tokens for deletion via SQS")
        sendingResults
      }
    }
}
