package com.gu.notificationschedule

import java.time.Instant

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.gu.notificationschedule.cloudwatch.{CloudWatch, CloudWatchImpl}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistence, NotificationSchedulePersistenceImpl}
import com.gu.scanamo.Scanamo.exec
import com.gu.scanamo.Table
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.syntax._
import com.gu.{AppIdentity, AwsIdentity}
import org.apache.logging.log4j.LogManager



case class NotificationScheduleConfig(app: String, stage: String, stack: String) {
  val notificationScheduleTable: String = s"$app-$stage-$stack-mobile-notifications-schedule"
}



class ProcessNotificationScheduleLambda(config: NotificationScheduleConfig, cloudWatch: CloudWatch, notificationSchedulePersistence: NotificationSchedulePersistence) {
  def this(config: NotificationScheduleConfig, lambdaName: String) = this(config, new CloudWatchImpl(config.stage, lambdaName, AmazonCloudWatchAsyncClientBuilder.defaultClient()), new NotificationSchedulePersistenceImpl(config, AmazonDynamoDBAsyncClientBuilder.defaultClient()) )

  def this() = this(AppIdentity.whoAmI(defaultAppName = "mobile-notifications-schedule") match {
    case awsIdentity: AwsIdentity => NotificationScheduleConfig(awsIdentity.app, awsIdentity.stage, awsIdentity.stack)
    case _ => throw new IllegalStateException("Not in aws")
  }, "MobileNotificationSchedule")

  val logger = LogManager.getLogger(classOf[ProcessNotificationScheduleLambda])

  def apply(): Unit = {
    val notificationsScheduleEntries = notificationSchedulePersistence.query()
    val nowEpoch = Instant.now().getEpochSecond
    notificationsScheduleEntries.foreach(notificationSchedulePersistence.write(_, true, nowEpoch))

    cloudWatch.sendMetricsSoFar()
  }
}
