package com.gu.notifications.worker

import java.util.UUID

import cats.effect.IO
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.{ApnsDeliverySuccess, DeliveryException, DeliveryService}
import com.gu.notifications.worker.delivery.apns.ApnsClient
import _root_.models._
import _root_.models.Link._
import _root_.models.Importance._
import _root_.models.TopicTypes._
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.models.SendingResults
import com.gu.notifications.worker.utils.Cloudwatch
import fs2.{Chunk, Sink, Stream}
import org.slf4j.Logger
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

import scala.collection.JavaConverters._

class WorkerRequestHandlerSpec extends Specification with Matchers {

  "the WorkerRequestHandler" should {
    "Send one notification" in new WRHSScope {
      workerRequestHandler.handleRequest(sqsEvent, null)

      deliveryCallsCount shouldEqual 1
      cloudwatchCallsCount shouldEqual 1
      cleaningCallsCount shouldEqual 1
      sendingResults shouldEqual Some(SendingResults(1, 0, 0))
      tokensToCleanCount shouldEqual 0
    }

    "Clean invalid tokens" in new WRHSScope {
      override def deliveries: Stream[IO, Either[DeliveryException, ApnsDeliverySuccess]] =
        Stream(
          Left(InvalidToken(UUID.randomUUID, "invalid token", "test"))
        )

      workerRequestHandler.handleRequest(sqsEvent, null)

      deliveryCallsCount shouldEqual 1
      cloudwatchCallsCount shouldEqual 1
      cleaningCallsCount shouldEqual 1
      sendingResults shouldEqual Some(SendingResults(0, 1, 0))
      tokensToCleanCount shouldEqual 1
    }

    "Count dry runs" in new WRHSScope {
      override def deliveries: Stream[IO, Either[DeliveryException, ApnsDeliverySuccess]] =
        Stream(
          Right(ApnsDeliverySuccess("token", dryRun = true))
        )

      workerRequestHandler.handleRequest(sqsEvent, null)

      deliveryCallsCount shouldEqual 1
      cloudwatchCallsCount shouldEqual 1
      cleaningCallsCount shouldEqual 1
      sendingResults shouldEqual Some(SendingResults(0, 0, 1))
      tokensToCleanCount shouldEqual 0
    }
  }


  trait WRHSScope extends Scope  {

    val notification = BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.BreakingNews,
      title  = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = None,
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = None,
      importance = Major,
      topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))
    )
    val shardedNotification = ShardedNotification(
      notification = notification,
      range = ShardRange(0, 1)
    )

    val sqsEvent: SQSEvent = {
      val event = new SQSEvent()
      val sqsMessage = new SQSMessage()
      sqsMessage.setBody(Json.stringify(Json.toJson(shardedNotification)))
      event.setRecords(List(sqsMessage).asJava)
      event
    }


    def deliveries: Stream[IO, Either[DeliveryException, ApnsDeliverySuccess]] = Stream(Right(ApnsDeliverySuccess("token")))

    var deliveryCallsCount = 0
    var cleaningCallsCount = 0
    var tokensToCleanCount = 0
    var cloudwatchCallsCount = 0
    var sendingResults: Option[SendingResults] = None

    val workerRequestHandler = new WorkerRequestHandler[ApnsClient] {
      override def platform: Platform = iOS

      override def deliveryService: IO[DeliveryService[IO, ApnsClient]] = IO.pure(new DeliveryService[IO, ApnsClient] {
        override def send(notification: Notification, shardRange: ShardRange, platform: Platform): Stream[IO, Either[DeliveryException, ApnsDeliverySuccess]] = {
          deliveryCallsCount += 1
          deliveries
        }
      })

      override val cleaningClient: CleaningClient = new CleaningClient {
        override def sendInvalidTokensToCleaning(implicit logger: Logger): Sink[IO, Chunk[String]] = { stream =>
          cleaningCallsCount += 1
          stream.map { chunk =>
            tokensToCleanCount += chunk.size
            ()
          }
        }
      }

      override val cloudwatch: Cloudwatch = new Cloudwatch {
        override def sendMetrics(stage: String, platform: Platform): Sink[IO, SendingResults] = { stream =>
          cloudwatchCallsCount += 1
          stream.map { results =>
            sendingResults = Some(results)
            ()
          }
        }
      }
    }
  }

}
