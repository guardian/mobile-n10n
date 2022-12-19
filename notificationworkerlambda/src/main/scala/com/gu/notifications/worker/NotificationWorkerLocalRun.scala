package com.gu.notifications.worker

import _root_.models._
import _root_.models.TopicTypes._
import _root_.models.Link._
import _root_.models.Importance._
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.gu.notifications.worker.tokens.ChunkedTokens
import play.api.libs.json.Json

import java.util.UUID
import scala.jdk.CollectionConverters._
import java.time.Instant
object NotificationWorkerLocalRun extends App {
  val notification = BreakingNewsNotification(
    id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
    `type` = NotificationType.BreakingNews,
    title = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
    message = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
    thumbnailUrl = None,
    sender = "matt.wells@guardian.co.uk",
    link = Internal(
      "world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
      Some("https://gu.com/p/4p7xt"),
      GITContent,None,
    ),
    imageUrl = None,
    importance = Major,
    topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international")),
    dryRun = None
  )

  val tokens = ChunkedTokens(
    notification = notification,
    range = ShardRange(0, 1),
    tokens = List("token"),
    notificationAppReceivedTime = Some(Instant.now())
  )

  val sqsEvent: SQSEvent = {
    val event = new SQSEvent()
    val sqsMessage = new SQSMessage()
    sqsMessage.setBody(Json.stringify(Json.toJson(tokens)))
    sqsMessage.setAttributes((Map("SentTimestamp" -> s"${Instant.now.toEpochMilli}").asJava))
    event.setRecords(List(sqsMessage).asJava)
    event
  }

  args.lastOption.map(_.toLowerCase) foreach {
    case "android" => new AndroidSender().handleChunkTokens(sqsEvent, null)
    case "ios"     => new IOSSender().handleChunkTokens(sqsEvent, null)
    case _         => println("invalid option")
  }

}
