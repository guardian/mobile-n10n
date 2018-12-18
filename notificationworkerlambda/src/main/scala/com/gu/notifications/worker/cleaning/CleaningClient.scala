package com.gu.notifications.worker.cleaning

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClient}
import com.gu.notifications.worker.delivery.DeliveryClient
import com.gu.notifications.worker.models.InvalidTokens
import fs2.{Chunk, Sink}
import org.slf4j.Logger
import play.api.libs.json.Json
import utils.MobileAwsCredentialsProvider

class CleaningClient(sqsUrl: String) {
  val credentialsProvider = new MobileAwsCredentialsProvider

  val sqsClient: AmazonSQS = AmazonSQSClient
    .builder()
    .withCredentials(credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build


  def sendInvalidTokensToCleaning[C <: DeliveryClient](implicit logger: Logger): Sink[IO, Chunk[String]] =
    _.evalMap { chunk =>
      IO.delay {
        val json = Json.stringify(Json.toJson(InvalidTokens(chunk.toList)))
        sqsClient.sendMessage(sqsUrl, json)
        logger.info(s"Sent ${chunk.size} tokens for deletion via SQS")
      }
    }
}
