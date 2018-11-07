package com.gu.notifications.worker.utils

import cats.effect.IO
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClient}
import com.gu.notifications.worker.delivery.DeliveryException.DryRun
import com.gu.notifications.worker.delivery.{DeliveryClient, DeliveryException}
import com.gu.notifications.worker.models.InvalidTokens
import fs2.Pipe
import org.slf4j.Logger
import play.api.libs.json.Json
import utils.MobileAwsCredentialsProvider

class Reporting(sqsUrl: String) {

  val credentialsProvider = new MobileAwsCredentialsProvider

  val sqsClient: AmazonSQS = AmazonSQSClient
    .builder()
    .withCredentials(credentialsProvider)
    .withRegion("eu-west-1")
    .build

  def log[C <: DeliveryClient](prefix: String)(implicit logger: Logger): Pipe[IO, Either[DeliveryException, C#Success], Either[DeliveryException, C#Success]] =
    _.evalMap { resp =>
      IO.delay {
        resp match {
          case Left(DryRun(_, _)) => () // no need to trace each individual token in the logs
          case Left(e) => logger.error(s"$prefix $e")
          case Right(_) => () // doing nothing when success
        }
        resp
      }
    }

  def report[C <: DeliveryClient](implicit logger: Logger): Pipe[IO, InvalidTokens, Unit] =
    _.evalMap { invalidTokens =>
      IO.delay {
        val batches = invalidTokens.tokens.grouped(1000).map(InvalidTokens.apply)
        batches.foreach { batch =>
          val json = Json.stringify(Json.toJson(batch))
          sqsClient.sendMessage(sqsUrl, json)
        }
      }
    }

}
