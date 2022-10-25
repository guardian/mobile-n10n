package com.gu.notifications.ec2worker

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.tokens.ChunkedTokens
import models.BreakingNewsNotification
import java.util.UUID
import models.GITContent
import models.Link
import models.ShardRange
import models.Topic
import models.TopicTypes
import models.Importance
import play.api.libs.json.Json
import java.time.Instant
import com.gu.notifications.ec2worker.IOSSender
import com.gu.notifications.ec2worker.AndroidSender
import models.NotificationType
import scala.jdk.CollectionConverters._
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}

object SenderWorker extends App {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val notification = BreakingNewsNotification(
    id = UUID.randomUUID(),
    `type` = NotificationType.BreakingNews,
    title = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
    message = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
    thumbnailUrl = None,
    sender = "matt.wells@guardian.co.uk",
    link = Link.Internal(
      "world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
      Some("https://gu.com/p/4p7xt"),
      GITContent,None,
    ),
    imageUrl = None,
    importance = Importance.Major,
    topic = List(Topic(TopicTypes.Breaking, "uk"), Topic(TopicTypes.Breaking, "us"), Topic(TopicTypes.Breaking, "au"), Topic(TopicTypes.Breaking, "international")),
    dryRun = Some(true)
  )

  val tokens = ChunkedTokens(
    notification = notification,
    range = ShardRange(0, 1),
    tokens = List("token")
  )

  val sqsEvent: SQSEvent = {
    val event = new SQSEvent()
    val sqsMessage = new SQSMessage()
    sqsMessage.setBody(Json.stringify(Json.toJson(tokens)))
    sqsMessage.setAttributes((Map("SentTimestamp" -> s"${Instant.now.toEpochMilli}").asJava))
    event.setRecords(List(sqsMessage).asJava)
    event
  }

  println("Sender worker started")
  logger.info("Sender worker started")

  new AndroidSender(Android).handleChunkTokens(sqsEvent, null)
  new AndroidSender(AndroidBeta).handleChunkTokens(sqsEvent, null)
  new AndroidSender(AndroidEdition).handleChunkTokens(sqsEvent, null)
  new IOSSender(Ios).handleChunkTokens(sqsEvent, null)
  new IOSSender(IosEdition).handleChunkTokens(sqsEvent, null)
  Thread.sleep(60*60*1000)
}
