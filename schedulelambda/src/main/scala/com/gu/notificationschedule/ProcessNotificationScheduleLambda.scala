package com.gu.notificationschedule

import java.time.{Clock, Instant}
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.notificationschedule.ProcessNotificationScheduleLambda.{lambdaClock, lambdaCloudWatch, lambdaConfig, lambdaOkHttpClient}
import com.gu.notificationschedule.cloudwatch.{CloudWatch, CloudWatchImpl}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceImpl, NotificationSchedulePersistenceSync, NotificationsScheduleEntry}
import com.gu.notificationschedule.external.{SsmConfig, SsmConfigLoader}
import com.gu.notificationschedule.notifications.{RequestNotification, RequestNotificationImpl}
import okhttp3.OkHttpClient
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


class NotificationScheduleConfig(ssmConfig: SsmConfig) {
  val notificationScheduleTable: String = s"${ssmConfig.app}-${ssmConfig.stage}-${ssmConfig.stack}"
  val stage: String = ssmConfig.stage
  val pushTopicsUrl: String = ssmConfig.config.getString("schedule.notifications.pushTopicUrl")
  val apiKey: String = ssmConfig.config.getString("schedule.notifications.secretKey")
}

object ProcessNotificationScheduleLambda {

  private lazy val lambdaOkHttpClient = new OkHttpClient.Builder().build()
  private lazy val lambdaConfig = AppIdentity.whoAmI(defaultAppName = "mobile-notifications-schedule") match {
    case _: AwsIdentity => new NotificationScheduleConfig(SsmConfigLoader.load())
    case _ => throw new IllegalStateException("Not in aws")
  }
  private lazy val lambdaCloudWatch = new CloudWatchImpl(
    lambdaConfig.stage,
    "MobileNotificationSchedule",
    AmazonCloudWatchAsyncClientBuilder.defaultClient())(ExecutionContext.Implicits.global)
  private lazy val lambdaClock = Clock.systemUTC()
}

class ProcessNotificationScheduleLambda(
                                         config: NotificationScheduleConfig,
                                         cloudWatch: CloudWatch,
                                         notificationSchedulePersistence: NotificationSchedulePersistenceSync,
                                         requestNotification: RequestNotification,
                                         clock: Clock
                                       ) {

  val logger: Logger = LoggerFactory.getLogger(classOf[ProcessNotificationScheduleLambda])

  def this() = this(
    lambdaConfig,
    lambdaCloudWatch,
    new NotificationSchedulePersistenceImpl(lambdaConfig.notificationScheduleTable, AmazonDynamoDBAsyncClientBuilder.defaultClient()),
    new RequestNotificationImpl(lambdaConfig, lambdaOkHttpClient, lambdaCloudWatch),
    lambdaClock
  )


  def apply(): Unit = {
    val queryTried: Try[Seq[NotificationsScheduleEntry]] = queryNotificationsToSchedule
    queryTried.flatMap(sendNotificationsAndStoreSent).map(_ => cloudWatch.sendMetricsSoFar()) match {
      case Success(_) => ()
      case Failure(t) => {
        logger.warn("Error executing schedule", t)
        throw t
      }
    }
  }


  private def queryNotificationsToSchedule: Try[Seq[NotificationsScheduleEntry]] = cloudWatch.timeTry("query", () => Try {
    val notificationsScheduleEntries = notificationSchedulePersistence.querySync()
    cloudWatch.queueMetric("discovered", notificationsScheduleEntries.size, StandardUnit.Count)
    notificationsScheduleEntries
  })


  private def sendNotificationsAndStoreSent(notificationsScheduleEntries: Seq[NotificationsScheduleEntry]): Try[Unit] = {
    val nowEpoch = Instant.now(clock).getEpochSecond
    val triedSendNotifications = notificationsScheduleEntries.map(notificationsScheduleEntry => sendNotificationAndStoreSent(nowEpoch, notificationsScheduleEntry))
    if (triedSendNotifications.exists(_.isFailure)) {
      Failure(new Exception(s"Notifications failed ${triedSendNotifications.size}"))
    }
    else {
      Success(())
    }
  }


  private def sendNotificationAndStoreSent(nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry): Try[Unit] =
    requestNotification(nowEpoch, notificationsScheduleEntry)
      .flatMap(_ => setSent(nowEpoch, notificationsScheduleEntry))


  private def setSent(nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry) =
    cloudWatch.timeTry("set-sent", () => {
      val attemptPersistence = Try {
        notificationSchedulePersistence.writeSync(notificationsScheduleEntry, Some(nowEpoch))
      }
      attemptPersistence.fold((t: Throwable) => logger.warn(s"Error persisting sent $notificationsScheduleEntry", t), _ => ())
      attemptPersistence
    })
}
