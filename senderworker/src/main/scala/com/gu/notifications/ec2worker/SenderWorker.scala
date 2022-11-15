package com.gu.notifications.ec2worker

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.events.SQSEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.amazon.sqs.javamessaging.{ProviderConfiguration, SQSConnection, SQSConnectionFactory, SQSQueueDestination}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs
import com.amazonaws.services.sqs.{AmazonSQSAsyncClientBuilder, AmazonSQSClientBuilder}
import com.gu.notifications.worker.IOSSender
import com.gu.notifications.worker.AndroidSender
import com.gu.notifications.worker.utils.Aws.credentialsProvider

import java.util.concurrent.TimeUnit
import javax.jms
import javax.jms.{Destination, MessageConsumer, MessageListener, ObjectMessage, Queue, Session, TextMessage}
import javax.security.auth.callback.Callback
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object SenderWorker extends App {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val sqsClient = AmazonSQSClientBuilder.standard()
    .withCredentials(credentialsProvider)
    .withRegion("eu-west-1")
    .build()

  class OpenConnection(queueName: String) {
    val connectionFactory = new SQSConnectionFactory(
      new ProviderConfiguration(),
      sqsClient
    )

    val connection: SQSConnection = connectionFactory.createConnection()

    val connectionSession: Session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

    val consumer: MessageConsumer = connectionSession.createConsumer(connectionSession.createQueue(queueName))

    connection.start()
  }

  class ReceiverCallback(queueUrl: String, handleChunkTokens: (SQSEvent, Context) => Unit) extends MessageListener {

    def parsedReceivedMessages(message: jms.Message): SQSEvent = {

      message match {
        case textMessage: TextMessage => println(s"Text message: ${textMessage.getText}")
        case objectMessage: ObjectMessage => println(s"Object message: ${objectMessage.getObject}")
      }

      //      val parsedMessages = {
      //        val sqsMessage = new SQSMessage()
      //        sqsMessage.setBody(message)
      //        sqsMessage.setAttributes(message)
      //        sqsMessage.setReceiptHandle(message.getReceiptHandle)
      //        sqsMessage
      //      }

      val event = new SQSEvent()
      //            event.setRecords(parsedMessages.asJava)
      event
    }

//    val parsedEvent: SQSEvent = parsedReceivedMessages()

    override def onMessage(message: jms.Message): Unit = {
      println("JMS message received")
      handleChunkTokens(parsedReceivedMessages(message), null)
      message.acknowledge()
    }
  }

  def listenForMessages(queueName: String, queueUrl: String, appName: String, handleChunkTokens: (SQSEvent, Context) => Unit): Unit = {
    logger.info(s"About to poll queue - $appName")

    val connectionCallback = new ReceiverCallback(queueUrl, handleChunkTokens)

    new OpenConnection(queueName) {
      consumer.setMessageListener(connectionCallback)
    }

    logger.info(s"Polling for messages from $appName queue")

  }

  println("Sender workers start")
  logger.info("Sender workers start")
  val config = Configuration.fetchConfiguration()

  logger.info("Sender worker - Ios started")
  val iosSender = new IOSSender(Configuration.fetchApns(config, Ios))
  listenForMessages("notification-sender-workers-ec2-CODE-SenderSqsios636F1B52-khVE8tvZpkqb", iosSender.config.sqsUrl, "ios", iosSender.handleChunkTokens)

//  logger.info("Sender worker - Android started")
//  val androidSender = new AndroidSender(Configuration.fetchFirebase(config, Android), Some(Android.toString()))
//  listenForMessages(androidSender.config.sqsName, androidSender.config.sqsUrl, "android", androidSender.handleChunkTokens)
//
//  logger.info("Sender worker - IosEdition started")
//  val iosEditionSender = new IOSSender(Configuration.fetchApns(config, IosEdition))
//  listenForMessages(iosEditionSender.config.sqsName, iosEditionSender.config.sqsUrl, "ios-edition", iosEditionSender.handleChunkTokens)
//
//  logger.info("Sender worker - AndroidBeta started")
//  val androidBetaSender = new AndroidSender(Configuration.fetchFirebase(config, AndroidBeta), Some(AndroidBeta.toString()))
//  listenForMessages(androidBetaSender.config.sqsName, androidBetaSender.config.sqsUrl,"android-beta", androidBetaSender.handleChunkTokens)
//
//  logger.info("Sender worker - AndroidEdition started")
//  val androidEditionSender = new AndroidSender(Configuration.fetchFirebase(config, AndroidEdition), Some(AndroidEdition.toString()))
//  listenForMessages(androidEditionSender.config.sqsName, androidEditionSender.config.sqsUrl, "android-edition", androidEditionSender.handleChunkTokens)

//  logger.info("Sender worker all started")

}
