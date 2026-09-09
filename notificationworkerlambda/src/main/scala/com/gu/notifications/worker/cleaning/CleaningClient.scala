package com.gu.notifications.worker.cleaning

import cats.effect.IO
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import com.gu.notifications.worker.models.InvalidTokens
import com.gu.notifications.worker.utils.Aws
import fs2.{Chunk, Pipe}
import org.slf4j.Logger
import play.api.libs.json.Json

trait CleaningClient {
  def sendInvalidTokensToCleaning(implicit logger: Logger): Pipe[IO, Chunk[String], Unit]
}

class CleaningClientImpl(sqsUrl: String) extends CleaningClient {

  val sqsClient: SqsClient = SqsClient
    .builder()
    .credentialsProvider(Aws.credentialsProviderV2)
    .region(Region.EU_WEST_1)
    .build()

  private def sendTokensToQueue(tokens: List[String])(implicit logger: Logger): Unit = {
    val json = Json.stringify(Json.toJson(InvalidTokens(tokens)))
    sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(sqsUrl).messageBody(json).build())
    logger.info(s"Sent ${tokens.size} tokens for deletion via SQS")
  }

  def sendInvalidTokensToCleaning(implicit logger: Logger): Pipe[IO, Chunk[String], Unit] =
    _.evalMap { chunk =>
      IO.delay {
        sendTokensToQueue(chunk.toList)
      }
    }
}
