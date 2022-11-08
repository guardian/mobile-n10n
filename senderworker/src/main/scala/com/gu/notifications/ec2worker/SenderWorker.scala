package com.gu.notifications.ec2worker

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.events.SQSEvent

import scala.concurrent.ExecutionContext.Implicits.global

import scala.jdk.CollectionConverters._
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.{AmazonSQSAsyncClientBuilder, AmazonSQSClientBuilder}
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageRequest}
import com.gu.notifications.worker.IOSSender
import com.gu.notifications.worker.AndroidSender
import com.gu.notifications.worker.utils.Aws.credentialsProvider

import scala.concurrent.Future

object SenderWorker extends App {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val sqsClient = AmazonSQSAsyncClientBuilder.standard()
    .withCredentials(credentialsProvider)
    .withRegion("eu-west-1")
    .build()

  def pollQueue(queue: String, handleChunkTokens: (SQSEvent, Context) => Unit): Future[Unit] = Future {
    logger.info(s"About to poll queue - $queue")
    val receiveMessages = sqsClient.receiveMessage(new ReceiveMessageRequest().withQueueUrl(queue).withWaitTimeSeconds(20)).getMessages.asScala.toList

    def parsedReceivedMessages(message: List[Message]): SQSEvent = {
      val parsedMessages = message.map(m => {
        val sqsMessage = new SQSMessage()
        sqsMessage.setBody(m.getBody)
        sqsMessage.setAttributes(m.getAttributes)
        sqsMessage.setReceiptHandle(m.getReceiptHandle)
        sqsMessage
      })

      val event = new SQSEvent()
      event.setRecords(parsedMessages.asJava)
      event
    }

    val parsedEvent: SQSEvent = parsedReceivedMessages(receiveMessages)

    handleChunkTokens(parsedEvent, null)

    parsedEvent.getRecords.forEach(m => {
      sqsClient.deleteMessage(queue, m.getReceiptHandle)
      logger.info("Successfully deleted message")
    })

    logger.info(s"Finished polling $queue")

  }

  println("Sender workers start")
  logger.info("Sender workers start")
  val config = Configuration.fetchConfiguration()

  logger.info("Sender worker - Ios started")
  val iosSender = new IOSSender(Configuration.fetchApns(config, Ios))
  pollQueue(iosSender.config.sqsUrl, iosSender.handleChunkTokens)

  logger.info("Sender worker - Android started")
  val androidSender = new AndroidSender(Configuration.fetchFirebase(config, Android), Some(Android.toString()))
  pollQueue(androidSender.config.sqsUrl, androidSender.handleChunkTokens)

  logger.info("Sender worker - IosEdition started")
  val iosEditionSender = new IOSSender(Configuration.fetchApns(config, IosEdition))
  pollQueue(iosEditionSender.config.sqsUrl, iosEditionSender.handleChunkTokens)

  logger.info("Sender worker - AndroidBeta started")
  val androidBetaSender = new AndroidSender(Configuration.fetchFirebase(config, AndroidBeta), Some(AndroidBeta.toString()))
  pollQueue(androidBetaSender.config.sqsUrl, androidBetaSender.handleChunkTokens)

  logger.info("Sender worker - AndroidEdition started")
  val androidEditionSender = new AndroidSender(Configuration.fetchFirebase(config, AndroidEdition), Some(AndroidEdition.toString()))
  pollQueue(androidEditionSender.config.sqsUrl, androidEditionSender.handleChunkTokens)

  logger.info("Sender worker all started")
  Thread.sleep(60*60*1000)
}
