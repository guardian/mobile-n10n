package com.gu.notificationschedule

import java.time.Instant

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.gu.notificationschedule.cloudwatch.{CloudWatch, CloudWatchImpl}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistence, NotificationSchedulePersistenceImpl, NotificationsScheduleEntry}
import com.gu.{AppIdentity, AwsIdentity}
import org.apache.logging.log4j.{LogManager, Logger}

import scala.util.{Failure, Success, Try}


case class NotificationScheduleConfig(app: String, stage: String, stack: String) {
  val notificationScheduleTable: String = s"$app-$stage-$stack-mobile-notifications-schedule"
}


class ProcessNotificationScheduleLambda(config: NotificationScheduleConfig, cloudWatch: CloudWatch, notificationSchedulePersistence: NotificationSchedulePersistence) {
  def this(config: NotificationScheduleConfig, lambdaName: String) = this(config, new CloudWatchImpl(config.stage, lambdaName, AmazonCloudWatchAsyncClientBuilder.defaultClient()), new NotificationSchedulePersistenceImpl(config, AmazonDynamoDBAsyncClientBuilder.defaultClient()))

  def this() = this(AppIdentity.whoAmI(defaultAppName = "mobile-notifications-schedule") match {
    case awsIdentity: AwsIdentity => NotificationScheduleConfig(awsIdentity.app, awsIdentity.stage, awsIdentity.stack)
    case _ => throw new IllegalStateException("Not in aws")
  }, "MobileNotificationSchedule")

  val logger: Logger = LogManager.getLogger(classOf[ProcessNotificationScheduleLambda])

  def apply(): Unit = {
    val timer = cloudWatch.startTimer("lambda")
    val triedFetch: Try[Seq[NotificationsScheduleEntry]] = Try {
      notificationSchedulePersistence.query()
    }
    val triedAll: Try[Unit] = triedFetch match {
      case Success(notificationsScheduleEntries) => triggerNotifications(notificationsScheduleEntries)
      case failure => {
        cloudWatch.queueMetric("discovery-failed", 1, StandardUnit.Count)
        failure.map(_ => ())
      }
    }
    triedAll.fold(throwable => timer.fail, unit => timer.succeed)
    cloudWatch.sendMetricsSoFar()
  }

  private def triggerNotifications(notificationsScheduleEntries: Seq[NotificationsScheduleEntry]) = {
    cloudWatch.queueMetric("discovered", notificationsScheduleEntries.size, StandardUnit.Count)
    val nowEpoch = Instant.now().getEpochSecond
    val triedUnits = notificationsScheduleEntries.map(notificationsScheduleEntry => triggerNotification(nowEpoch, notificationsScheduleEntry))
    val failed = triedUnits.count(_.isFailure)
    cloudWatch.queueMetric("trigger-failed", failed, StandardUnit.Count)
    if(failed > 0) {
      Success(())
    }
    else {
      Failure(new Exception(s"$failed triggers failed out of ${triedUnits.size}"))
    }

  }

  private def triggerNotification(nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry) = {
    Try {
      logger.warn("TODO: trigger notification {}", notificationsScheduleEntry)
      notificationSchedulePersistence.write(notificationsScheduleEntry, true, nowEpoch)
    }.recover{
      case throwable: Throwable => {
        logger.warn(s"Failed to process notification: $notificationsScheduleEntry", throwable)
        Failure(throwable)
      }
    }
  }
}
