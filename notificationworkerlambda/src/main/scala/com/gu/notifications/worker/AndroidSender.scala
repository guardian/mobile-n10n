package com.gu.notifications.worker

import _root_.models.NotificationMetadata
import _root_.models.Topic
import cats.effect.{ContextShift, IO, Timer}
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.delivery.{BatchDeliverySuccess, DeliveryClient, DeliveryException}
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import com.gu.notifications.worker.models.{LatencyMetrics, SendingResults}
import com.gu.notifications.worker.tokens.{BatchNotification, ChunkedTokens, IndividualNotification}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, Reporting}
import fs2.{Pipe, Stream}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import com.gu.notifications.worker.delivery.fcm.FcmFirebase
import scala.util.Try

class AndroidSender(val config: FcmWorkerConfiguration, val firebaseAppName: Option[String], val metricNs: String) extends SenderRequestHandler[FcmClient] {

  // maybe we'll implement a metrics registry for android in the future?
  // it seems like a metrics registry could be a nice way to abstract all the metrics we care about collecting
  def this() = {
    this(Configuration.fetchFirebase(), None, "workers")
  }

  val cleaningClient = new CleaningClientImpl(config.cleaningSqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl(metricNs)

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threadPoolSize))

  logger.info(s"Using thread pool size: ${config.threadPoolSize}")
  logger.info(s"Concurrency for individual send: ${config.concurrencyForIndividualSend}")
  logger.info(s"Concurrency for message processing: ${config.concurrencyForMessages}")
  logger.info(s"HttpClient object pool: ${config.httpClientPoolSize}")
  logger.info(s"HttpClient connect timeout in sec: ${config.fcmConfig.connectTimeout}")
  logger.info(s"HttpClient request timeout in sec: ${config.fcmConfig.requestTimeout}")

  override implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  override implicit val timer: Timer[IO] = IO.timer(ec)

  val fcmFirebase: Try[FcmFirebase] = FcmFirebase(config.fcmConfig, firebaseAppName)

  val fcmClients: Seq[Try[FcmClient]] = Seq.fill(config.httpClientPoolSize)(fcmFirebase).map(firebase => firebase.map(FcmClient(_)))

  val deliveryServiceStream: Stream[IO, Fcm[IO]] = 
    Stream.emits(fcmClients).covary[IO].flatMap(_.fold(e => Stream.raiseError[IO](e), c => Stream.eval[IO, Fcm[IO]]( IO.pure(new Fcm(c)))))

  override val deliveryService: IO[Fcm[IO]] = 
    fcmFirebase.map(FcmClient(_)).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(c)))

  override val maxConcurrency = config.concurrencyForIndividualSend
  override val batchConcurrency = 100
    
  //override the deliverChunkedTokens method to validate the success of sending batch notifications to the FCM client. This implementation could be refactored in the future to make it more streamlined with APNs
  override def deliverChunkedTokens(chunkedTokenStream: Stream[IO, (ChunkedTokens, Long, Instant, Int, String)]): Stream[IO, Unit] = {
    chunkedTokenStream.map {
      case (chunkedTokens, sentTime, functionStartTime, sqsMessageBatchSize, awsRequestId) => {
        val commonLogFields = Map("notificationId" -> chunkedTokens.notification.id, "awsRequestId" -> awsRequestId)
          for {
            _ <- Stream.eval( IO { logger.info(commonLogFields ++ Map("worker.messagingApi" -> "Individual"), s"Sending notification ${chunkedTokens.notification.id} with individual API") } )
            deliverStream <- deliverIndividualNotificationStream(Stream.emits(chunkedTokens.toNotificationToSends).covary[IO])
                        .broadcastTo(
                          reportSuccesses(chunkedTokens, sentTime, functionStartTime, sqsMessageBatchSize, awsRequestId),
                          cleanupFailures,
                          trackProgress(chunkedTokens.notification.id))
          } yield deliverStream
      }
    }.parJoin(config.concurrencyForMessages)
  }

  override def deliverIndividualNotificationStream(individualNotificationStream: Stream[IO, IndividualNotification]): Stream[IO, Either[DeliveryException, FcmClient#Success]] = {
    for {
      resp <- individualNotificationStream.zip(deliveryServiceStream.repeat).map {
        case (individualNotification, deliveryService) => {
          deliveryService.send(individualNotification.notification, individualNotification.token)
        }
      }
      .parJoin(maxConcurrency)
      .evalTap(Reporting.log(s"Sending failure: "))
    } yield resp
  }
  def reportBatchSuccesses[C <: DeliveryClient](chunkedTokens: ChunkedTokens, sentTime: Long, functionStartTime: Instant, sqsMessageBatchSize: Int, awsRequestId: String): Pipe[IO, Either[DeliveryException, BatchDeliverySuccess], Unit] = { input =>
    val notificationLog = s"(notification: ${chunkedTokens.notification.id} ${chunkedTokens.range})"
    val enableAwsMetric = chunkedTokens.notification.dryRun match {
      case Some(true) => false
      case _ => true
    }
    input
      .fold(SendingResults.empty) { case (acc, resp) => SendingResults.aggregateBatch(acc, chunkedTokens.tokens.size, resp) }
      .evalTap(logInfoWithFields(logFields(env, chunkedTokens.notification, chunkedTokens.tokens.size, sentTime, functionStartTime, Configuration.platform, sqsMessageBatchSize, messagingApi = "Batch", awsRequestId = awsRequestId), prefix = s"Results $notificationLog: ").andThen(_.map(cloudwatch.sendPerformanceMetrics(env.stage, enableAwsMetric))))
      .through(cloudwatch.sendResults(env.stage, Configuration.platform))
  }

  def reportBatchLatency[C <: DeliveryClient](chunkedTokens: ChunkedTokens, metadata: NotificationMetadata): Pipe[IO, Either[DeliveryException, BatchDeliverySuccess], Unit] = { input =>
    val shouldPushMetricsToAws = chunkedTokens.notification.dryRun match {
      case Some(true) => false
      case _ => true
    }
    input
      .fold(List.empty[Long]) { case (acc, resp) => LatencyMetrics.collectBatchLatency(acc, resp, metadata.notificationAppReceivedTime) }
      .through(cloudwatch.sendLatencyMetrics(shouldPushMetricsToAws, env.stage, Configuration.platform, metadata.audienceSize))
  }

  def cleanupBatchFailures[C <: DeliveryClient](notificationId: UUID): Pipe[IO, Either[Throwable, BatchDeliverySuccess], Unit] = { input =>
    input
      .collect {
        case Right(batchSuccess) =>
          batchSuccess.responses.foldLeft(List[String]())((acc, res) => res match {
            case Left(InvalidToken(_, token, _, _)) =>
              logger.debug(Map("notificationId" -> notificationId), s"Invalid token $token")
              token :: acc
            case Left(_) => acc
            case Right(_) => acc
          })
      }
      .chunkN(1000)
      .through(cleaningClient.sendInvalidBatchTokensToCleaning)
  }

  def trackBatchProgress[C <: DeliveryClient](notificationId: UUID): Pipe[IO, Either[Throwable, BatchDeliverySuccess], Unit] = {
    input => input.evalMap(chunk => IO.delay(chunk match {
      case Left(_) => () // we log in other places if there's a catastrophic batch error
      case Right(batchSuccess) => logger.info(Map("notificationId" -> notificationId), s"Processed ${batchSuccess.responses.size} individual notification(s)")
    }))
  }
}
