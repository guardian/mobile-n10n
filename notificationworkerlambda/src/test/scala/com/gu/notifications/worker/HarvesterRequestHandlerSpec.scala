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
import db.BuildTier
import java.time.Instant
import com.gu.notifications.worker.models.PerformanceMetrics

class HarvesterRequestHandlerSpec extends Specification with Matchers {

  "the WorkerRequestHandler" should {
    "Queue one multi platform breaking news notification" in new WRHSScope {

      val workerRequestHandler = new TestHarvesterRequestHandler {
        override val allowedTopicsForEc2Sender: List[String] = List()
      }
      workerRequestHandler.processNotification(sqsEventShardNotification(breakingNewsNotification), tokenService)
      tokenStreamCount.get() shouldEqual 0
      tokenPlatformStreamCount.get() shouldEqual 1
      workerRequestHandler.lambdaDeliveries.firebaseSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.firebaseSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.apnsSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.apnsSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.firebaseEditionSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.firebaseEditionSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.apnsEditionSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.apnsEditionSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.firebaseBetaSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.firebaseBetaSqsDeliveriesTotal.get() shouldEqual 2002

      workerRequestHandler.ec2Deliveries.firebaseSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseEditionSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseEditionSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsEditionSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsEditionSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseBetaSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseBetaSqsDeliveriesTotal.get() shouldEqual 0
    }

    "Use lambda stack if some of the topics are not on the selected list" in new WRHSScope {

      val workerRequestHandler = new TestHarvesterRequestHandler {
        override val allowedTopicsForEc2Sender: List[String] = List("breaking/uk", "breaking/us", "breaking/international")
      }
      workerRequestHandler.processNotification(sqsEventShardNotification(breakingNewsNotification), tokenService)
      tokenStreamCount.get() shouldEqual 0
      tokenPlatformStreamCount.get() shouldEqual 1
      workerRequestHandler.lambdaDeliveries.firebaseSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.firebaseSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.apnsSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.apnsSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.firebaseEditionSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.firebaseEditionSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.apnsEditionSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.apnsEditionSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.lambdaDeliveries.firebaseBetaSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.lambdaDeliveries.firebaseBetaSqsDeliveriesTotal.get() shouldEqual 2002

      workerRequestHandler.ec2Deliveries.firebaseSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseEditionSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseEditionSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsEditionSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.apnsEditionSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseBetaSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.ec2Deliveries.firebaseBetaSqsDeliveriesTotal.get() shouldEqual 0
    }

    "Switch to EC2 stack if all of the topics are on the selected list" in new WRHSScope {

      val workerRequestHandler = new TestHarvesterRequestHandler {
        override val allowedTopicsForEc2Sender: List[String] = List("breaking/uk", "breaking/us", "breaking/international", "breaking/au")
      }
      workerRequestHandler.processNotification(sqsEventShardNotification(breakingNewsNotification), tokenService)
      tokenStreamCount.get() shouldEqual 0
      tokenPlatformStreamCount.get() shouldEqual 1
      workerRequestHandler.ec2Deliveries.firebaseSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.ec2Deliveries.firebaseSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.ec2Deliveries.apnsSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.ec2Deliveries.apnsSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.ec2Deliveries.firebaseEditionSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.ec2Deliveries.firebaseEditionSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.ec2Deliveries.apnsEditionSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.ec2Deliveries.apnsEditionSqsDeliveriesTotal.get() shouldEqual 2002
      workerRequestHandler.ec2Deliveries.firebaseBetaSqsDeliveriesCount.get() shouldEqual 3
      workerRequestHandler.ec2Deliveries.firebaseBetaSqsDeliveriesTotal.get() shouldEqual 2002

      workerRequestHandler.lambdaDeliveries.firebaseSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.firebaseSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.apnsSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.apnsSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.firebaseEditionSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.firebaseEditionSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.apnsEditionSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.apnsEditionSqsDeliveriesTotal.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.firebaseBetaSqsDeliveriesCount.get() shouldEqual 0
      workerRequestHandler.lambdaDeliveries.firebaseBetaSqsDeliveriesTotal.get() shouldEqual 0
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
      sqsMessage.setAttributes((Map("SentTimestamp" -> "10").asJava))
      event.setRecords(List(sqsMessage).asJava)
      event
    }

    val twoThousandTwoTokens: List[String] = Range(0,2002).map(num => s"token-$num").toList
    def tokenStream: Stream[IO, String] = Stream.emits(twoThousandTwoTokens)
    def tokenPlatformStream: Stream[IO, HarvestedToken] = Stream.emits(
      twoThousandTwoTokens.map(HarvestedToken(_, Android, None)) ::: 
      twoThousandTwoTokens.map(HarvestedToken(_, Ios, None)) :::
      twoThousandTwoTokens.map(HarvestedToken(_, Android, Some(BuildTier.BETA))) :::
      twoThousandTwoTokens.map(HarvestedToken(_, IosEdition, None)) :::
      twoThousandTwoTokens.map(HarvestedToken(_, AndroidEdition, None)))

    def sqsDeliveries: Stream[IO, Either[Throwable, Unit]] = Stream(Right(()))

    val tokenStreamCount = new AtomicInteger()
    val tokenPlatformStreamCount = new AtomicInteger()

    val tokenService = new TokenService[IO] {
      override def tokens(notification: Notification, shardRange: ShardRange): Stream[IO, HarvestedToken] = {
        tokenPlatformStreamCount.incrementAndGet()
        tokenPlatformStream
      }
    }

    case class SqsDeliveriesCount(
      var apnsSqsDeliveriesCount: AtomicInteger,
      var apnsEditionSqsDeliveriesCount: AtomicInteger,
      var firebaseSqsDeliveriesCount: AtomicInteger,
      var firebaseEditionSqsDeliveriesCount: AtomicInteger,
      var firebaseBetaSqsDeliveriesCount: AtomicInteger,

      var apnsSqsDeliveriesTotal: AtomicInteger,
      var apnsEditionSqsDeliveriesTotal: AtomicInteger,
      var firebaseSqsDeliveriesTotal: AtomicInteger,
      var firebaseEditionSqsDeliveriesTotal: AtomicInteger,
      var firebaseBetaSqsDeliveriesTotal: AtomicInteger
    )

    def createTestSqsDeliveryStack(deliveriesCount: SqsDeliveriesCount): SqsDeliveryStack = 
      SqsDeliveryStack(
        iosLiveDeliveryService = (chunkedTokens: ChunkedTokens) => {
          deliveriesCount.apnsSqsDeliveriesCount.incrementAndGet()
          deliveriesCount.apnsSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
          sqsDeliveries
        },
        androidLiveDeliveryService = (chunkedTokens: ChunkedTokens) => {
          deliveriesCount.firebaseSqsDeliveriesCount.incrementAndGet()
          deliveriesCount.firebaseSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
          sqsDeliveries
        },
        androidBetaDeliveryService = (chunkedTokens: ChunkedTokens) => {
          deliveriesCount.firebaseBetaSqsDeliveriesCount.incrementAndGet()
          deliveriesCount.firebaseBetaSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
          sqsDeliveries
        },
        iosEditionDeliveryService = (chunkedTokens: ChunkedTokens) => {
          deliveriesCount.apnsEditionSqsDeliveriesCount.incrementAndGet()
          deliveriesCount.apnsEditionSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
          sqsDeliveries
        },
        androidEditionDeliveryService = (chunkedTokens: ChunkedTokens) => {
          deliveriesCount.firebaseEditionSqsDeliveriesCount.incrementAndGet()
          deliveriesCount.firebaseEditionSqsDeliveriesTotal.addAndGet(chunkedTokens.tokens.size)
          sqsDeliveries
        }
      )

    trait TestHarvesterRequestHandler extends HarvesterRequestHandler {

      override val jdbcConfig: JdbcConfig = null

      var cloudwatchFailures = new AtomicInteger()

      var lambdaDeliveries = SqsDeliveriesCount(
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
      )

      var ec2Deliveries = SqsDeliveriesCount(
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
        new AtomicInteger(),
      )

      override val lambdaServiceSet: SqsDeliveryStack = createTestSqsDeliveryStack(lambdaDeliveries)

      override val ec2ServiceSet: SqsDeliveryStack = createTestSqsDeliveryStack(ec2Deliveries)

      override val cloudwatch: Cloudwatch = new Cloudwatch {
        override def sendResults(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit] = ???

        override def sendLatencyMetrics(shouldPushMetricsToAws: Boolean, stage: String, platform: Option[Platform]): Pipe[IO, List[Long], Unit] = ???

        override def sendPerformanceMetrics(stage: String, enablePerformanceMetric: Boolean): PerformanceMetrics => Unit = ???

        override def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit] = {
          cloudwatchFailures.incrementAndGet()
          _.map(_ => ())
        }
      }
    }
  }

}
