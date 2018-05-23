package com.gu.notificationshards

import java.util.UUID

import com.amazonaws.services.cloudwatch.{ AmazonCloudWatchAsyncClientBuilder}
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.notificationshards.cloudwatch.{CloudWatch, CloudWatchImpl}
import com.gu.notificationshards.external.Jackson
import com.gu.notificationshards.lambda.{AwsLambda}
import com.gu.scanamo.Table
import org.apache.logging.log4j.LogManager

case class NotificationsShard( uuid: String, notification: String, due: String)

case class NotificationShardsConfig(app: String, stage: String, stack: String) {
  val notificationShardsTable: String = s"$app-$stage-$stack-mobile-notifications-shards"
}

object ProcessNotificationShardsLambda {
  def lambdaFactory(config: NotificationShardsConfig): String => String = {
    val table = Table[NotificationsShard](config.notificationShardsTable)
    body =>
      val logger = LogManager.getLogger(classOf[ProcessNotificationShardsLambda])
      logger.info("Request {}", body)
      table.put(NotificationsShard(UUID.randomUUID().toString,Jackson.mapper.writeValueAsString(body), "due"))
      ""
  }
}

class ProcessNotificationShardsLambda(lambda: String => String, cloudWatch: CloudWatch) extends AwsLambda(function = lambda, cloudWatch = cloudWatch){

  def this(config: NotificationShardsConfig, lambdaName: String) = this(ProcessNotificationShardsLambda.lambdaFactory(config), new CloudWatchImpl(config.stage, lambdaName, AmazonCloudWatchAsyncClientBuilder.defaultClient()))
  def this() = this(AppIdentity.whoAmI(defaultAppName = "mobile-notifications-shards") match {
    case awsIdentity: AwsIdentity => NotificationShardsConfig(awsIdentity.app, awsIdentity.stage, awsIdentity.stack)
    case _                        => throw new IllegalStateException("Not in aws")
  }, "MobileNotificationShards")
}
