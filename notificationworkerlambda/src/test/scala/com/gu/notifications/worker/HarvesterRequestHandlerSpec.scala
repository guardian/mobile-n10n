package com.gu.notifications.worker

import java.util.UUID

import _root_.models.Importance._
import _root_.models.Link._
import _root_.models.TopicTypes._
import _root_.models._
import cats.effect.IO
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.gu.notifications.worker.models.SendingResults
import com.gu.notifications.worker.tokens.{ChunkedTokens, SqsDeliveryService, TokenService}
import com.gu.notifications.worker.utils.Cloudwatch
import fs2.{Sink, Stream}
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

import scala.collection.JavaConverters._

class HarvesterRequestHandlerSpec extends Specification with Matchers {

  "the WorkerRequestHandler" should {
    "Send one Android breaking news notification" in new WRHSScope {
      workerRequestHandler.handleHarvesting(sqsEventShardNotification(breakingNewsNotification, Android), null)
      tokenStreamCount shouldEqual 1
      firebaseSqsDeliveriesCount shouldEqual 1
      apnsSqsDeliveriesCount shouldEqual 0

    }
    "Queue one Android content notification" in new WRHSScope {
      workerRequestHandler.handleHarvesting(sqsEventShardNotification(contentNotification, Android), null)
      tokenStreamCount shouldEqual 1
      firebaseSqsDeliveriesCount shouldEqual 1
      apnsSqsDeliveriesCount shouldEqual 0
    }
    "Send one iOS breaking news notification" in new WRHSScope {
      workerRequestHandler.handleHarvesting(sqsEventShardNotification(breakingNewsNotification, iOS), null)
      tokenStreamCount shouldEqual 1
      firebaseSqsDeliveriesCount shouldEqual 0
      apnsSqsDeliveriesCount shouldEqual 1

    }
    "Queue one iOS content notification" in new WRHSScope {
      workerRequestHandler.handleHarvesting(sqsEventShardNotification(contentNotification, iOS), null)
      tokenStreamCount shouldEqual 1
      firebaseSqsDeliveriesCount shouldEqual 0
      apnsSqsDeliveriesCount shouldEqual 1
    }

  }


  trait WRHSScope extends Scope {

    val breakingNewsNotification = BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.BreakingNews,
      title = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = None,
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = None,
      importance = Major,
      topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))
    )
    val contentNotification = BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd8"),
      `type` = NotificationType.Content,
      title = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = None,
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = None,
      importance = Major,
      topic = List(Topic(Content, "some-content"))
    )

    def sqsEventShardNotification(notification: Notification, platform: Platform): SQSEvent = {
      val shardedNotification = ShardedNotification(
        notification = notification,
        range = ShardRange(0, 1),
        platform = platform
      )
      val event = new SQSEvent()
      val sqsMessage = new SQSMessage()
      sqsMessage.setBody(Json.stringify(Json.toJson(shardedNotification)))
      event.setRecords(List(sqsMessage).asJava)
      event
    }


    def tokenStream: Stream[IO, String] = Stream("token1")

    def sqsDeliveries: Stream[IO, Either[Throwable, Unit]] = Stream(Right(()))


    var cloudwatchFailures = 0
    var tokenStreamCount = 0
    var apnsSqsDeliveriesCount = 0
    var firebaseSqsDeliveriesCount = 0

    val workerRequestHandler = new HarvesterRequestHandler {

      override val tokenService: TokenService[IO] = (notification: Notification, shardRange: ShardRange, platform: Platform) => {
        tokenStreamCount += 1
        tokenStream
      }
      override val apnsDeliveryService: SqsDeliveryService[IO] = (chunkedTokens: ChunkedTokens) => {
        apnsSqsDeliveriesCount += 1
        sqsDeliveries
      }

      override val firebaseDeliveryService: SqsDeliveryService[IO] = (chunkedTokens: ChunkedTokens) => {
        firebaseSqsDeliveriesCount += 1
        sqsDeliveries
      }
      override val cloudwatch: Cloudwatch = new Cloudwatch {
        override def sendMetrics(stage: String, platform: Platform): Sink[IO, SendingResults] = ???

        override def sendFailures(stage: String, platform: Platform): Sink[IO, Throwable] = {
          cloudwatchFailures = cloudwatchFailures + 1
          _.map(_ => ())
        }
      }
    }
  }

}
