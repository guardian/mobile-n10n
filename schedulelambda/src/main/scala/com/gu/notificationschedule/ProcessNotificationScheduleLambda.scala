package com.gu.notificationschedule

import java.time.Instant

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.gu.notificationschedule.cloudwatch.{CloudWatch, CloudWatchImpl}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceImpl, NotificationSchedulePersistenceSync, NotificationsScheduleEntry, ScheduleTableConfig}
import com.gu.{AppIdentity, AwsIdentity}
import org.apache.logging.log4j.{LogManager, Logger}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class ProcessNotificationScheduleLambda(config: ScheduleTableConfig, cloudWatch: CloudWatch, notificationSchedulePersistence: NotificationSchedulePersistenceSync) {

  def this(config: ScheduleTableConfig, lambdaName: String) = this(config, new CloudWatchImpl(config.stage, lambdaName, AmazonCloudWatchAsyncClientBuilder.defaultClient())(ExecutionContext.Implicits.global), new NotificationSchedulePersistenceImpl(config.scheduleTableName, AmazonDynamoDBAsyncClientBuilder.defaultClient()))

  def this() = this(AppIdentity.whoAmI(defaultAppName = "mobile-notifications-schedule") match {
    case awsIdentity: AwsIdentity => ScheduleTableConfig(awsIdentity.app, awsIdentity.stage, awsIdentity.stack)
    case _ => throw new IllegalStateException("Not in aws")
  }, "MobileNotificationSchedule")

  val logger: Logger = LogManager.getLogger(classOf[ProcessNotificationScheduleLambda])

  def apply(): Unit = {
    val timer = cloudWatch.startTimer("lambda")
    val triedFetch: Try[Seq[NotificationsScheduleEntry]] = Try {
      notificationSchedulePersistence.querySync()
    }
    val triedAll: Try[Unit] = triedFetch match {
      case Success(notificationsScheduleEntries) => triggerNotifications(notificationsScheduleEntries)
      case Failure(throwable) => {
        cloudWatch.queueMetric("discovery-failed", 1, StandardUnit.Count)
        logger.warn("Error running query", throwable)
        Failure(throwable)
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
      notificationSchedulePersistence.writeSync(notificationsScheduleEntry, Some(nowEpoch))
    }.recover{
      case throwable: Throwable => {
        logger.warn(s"Failed to process notification: $notificationsScheduleEntry", throwable)
        Failure(throwable)
      }
    }
  }
}
