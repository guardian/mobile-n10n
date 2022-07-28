package com.gu.notifications.workerlambda.cleaning

import cats.effect.IO
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import com.gu.notifications.workerlambda.models.InvalidTokens
import com.gu.notifications.workerlambda.utils.Aws
import fs2.{Chunk, Pipe}
import org.slf4j.Logger
import play.api.libs.json.Json
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

trait CleaningClient {
  def sendInvalidTokensToCleaning(implicit logger: Logger): Pipe[IO, Chunk[String], Unit]
}

class CleaningClientImpl(sqsUrl: String) extends CleaningClient {

  val sqsClient: SqsClient = SqsClient
    .builder
    .credentialsProvider(Aws.CredentialsProvider)
    .region(Region.EU_WEST_1)
    .build()


  def sendInvalidTokensToCleaning(implicit logger: Logger): Pipe[IO, Chunk[String], Unit] =
    _.evalMap { chunk =>
      IO.delay {
        val json = Json.stringify(Json.toJson(InvalidTokens(chunk.toList)))
        val sendMsgRequest: SendMessageRequest = SendMessageRequest.builder()
          .queueUrl(sqsUrl)
          .messageBody(json)
          .build()
        sqsClient.sendMessage(sendMsgRequest)
        logger.info(s"Sent ${chunk.size} tokens for deletion via SQS")
      }
    }
}
