package com.gu.notifications.worker

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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
import db.{HarvestedToken, JdbcConfig}
import fs2.{Pipe, Stream}
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

import scala.jdk.CollectionConverters._

class HarvesterRequestHandlerSpec extends Specification with Matchers {

  "the WorkerRequestHandler" should {
    "Queue one multi platform breaking news notification" in new WRHSScope {
      workerRequestHandler.doTheWork(sqsEventShardNotification(breakingNewsNotification), tokenService)
      tokenStreamCount.get() shouldEqual 0
      tokenPlatformStreamCount.get() shouldEqual 1
      firebaseSqsDeliveriesCount.get() shouldEqual 3
      apnsSqsDeliveriesCount.get() shouldEqual 3
      firebaseSqsDeliveriesTotal.get() shouldEqual 2002
      apnsSqsDeliveriesTotal.get() shouldEqual 2002
    }
  }


  trait WRHSScope extends Scope {

    val breakingNewsNotification = BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.BreakingNews,
      title = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
      message = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
      thumbnailUrl = None,
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent, None),
      imageUrl = None,
      importance = Major,
      topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international")),
      dryRun = None
    )
    val contentNotification = BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd8"),
      `type` = NotificationType.Content,
      title = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
      message = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
      thumbnailUrl = None,
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent, None),
      imageUrl = None,
      importance = Major,
      topic = List(Topic(Content, "some-content")),
      dryRun = None
    )

    def sqsEventShardNotification(notification: Notification): SQSEvent = {
      val shardedNotification = ShardedNotification(
        notification = notification,
        range = ShardRange(0, 1)
      )
      val event = new SQSEvent()
      val sqsMessage = new SQSMessage()
      sqsMessage.setBody(Json.stringify(Json.toJson(shardedNotification)))
      event.setRecords(List(sqsMessage).asJava)
      event
    }

    val twoThousandTwoTokens: List[String] = Range(0,2002).map(num => s"token-$num").toList
    def tokenStream: Stream[IO, String] = Stream.emits(twoThousandTwoTokens)
    def tokenPlatformStream: Stream[IO, HarvestedToken] = Stream.emits(twoThousandTwoTokens.map(HarvestedToken(_, Android, None)) ::: twoThousandTwoTokens.map(HarvestedToken(_, Ios, None)))

    def sqsDeliveries: Stream[IO, Either[Throwable, Unit]] = Stream(Right(()))


    var cloudwatchFailures = new AtomicInteger()
    val tokenStreamCount = new AtomicInteger()
    val tokenPlatformStreamCount = new AtomicInteger()
    var apnsSqsDeliveriesCount = new AtomicInteger()
    var firebaseSqsDeliveriesCount = new AtomicInteger()
    var apnsSqsDeliveriesTotal = new AtomicInteger()
    var firebaseSqsDeliveriesTotal = new AtomicInteger()

    val tokenService = new TokenService[IO] {
      override def tokens(notification: Notification, shardRange: ShardRange): Stream[IO, HarvestedToken] = {
        tokenPlatformStreamCount.incrementAndGet()
        tokenPlatformStream
      }
    }

    val workerRequestHandler = new HarvesterRequestHandler {

      override val jdbcConfig: JdbcConfig = null

      override val iosLiveDeliveryService: SqsDeliveryService[IO] = (chunkedTokens: ChunkedTokens) => {
        apnsSqsDeliveriesCount.incrementAndGet()
        apnsSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
        sqsDeliveries
      }

      override val androidLiveDeliveryService: SqsDeliveryService[IO] = (chunkedTokens: ChunkedTokens) => {
        firebaseSqsDeliveriesCount.incrementAndGet()
        firebaseSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
        sqsDeliveries
      }

      override val androidBetaDeliveryService: SqsDeliveryService[IO] = (chunkedTokens: ChunkedTokens) => {
       firebaseSqsDeliveriesCount.incrementAndGet()
       firebaseSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
       sqsDeliveries
      }

      override val iosEditionDeliveryService: SqsDeliveryService[IO] = (chunkedTokens: ChunkedTokens) => sqsDeliveries

      override val androidEditionDeliveryService: SqsDeliveryService[IO] = (chunkedTokens: ChunkedTokens) => sqsDeliveries

      override val cloudwatch: Cloudwatch = new Cloudwatch {
        override def sendMetrics(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit] = ???

        override def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit] = {
          cloudwatchFailures.incrementAndGet()
          _.map(_ => ())
        }
      }
    }
  }

}
