package com.gu.notifications.worker.cleaning

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClient}
import com.gu.notifications.worker.models.InvalidTokens
import com.gu.notifications.worker.utils.Aws
import fs2.{Chunk, Pipe}
import org.slf4j.Logger
import play.api.libs.json.Json

trait CleaningClient {
  def sendInvalidTokensToCleaning(implicit logger: Logger): Pipe[IO, Chunk[String], Unit]
}

class CleaningClientImpl(sqsUrl: String) extends CleaningClient {

  val sqsClient: AmazonSQS = AmazonSQSClient
    .builder()
    .withCredentials(Aws.credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build

  private def sendTokensToQueue(tokens: List[String])(implicit logger: Logger): Unit = {
    val json = Json.stringify(Json.toJson(InvalidTokens(tokens)))
    sqsClient.sendMessage(sqsUrl, json)
    logger.info(s"Sent ${tokens.size} tokens for deletion via SQS")
  }

  def sendInvalidTokensToCleaning(implicit logger: Logger): Pipe[IO, Chunk[String], Unit] =
    _.evalMap { chunk =>
      IO.delay {
        sendTokensToQueue(chunk.toList)
      }
    }

  def sendInvalidBatchTokensToCleaning(implicit logger: Logger): Pipe[IO, Chunk[List[String]], Unit] = {
    _.evalMap { chunk =>
      IO.delay {
        sendTokensToQueue(chunk.toList.flatten)
      }
    }
  }

}
