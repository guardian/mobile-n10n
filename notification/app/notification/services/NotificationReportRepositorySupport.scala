package notification.services

import javax.inject.Inject

import aws.AsyncDynamo
import com.amazonaws.regions.Regions.EU_WEST_1
import tracking.{DynamoNotificationReportRepository, SentNotificationReportRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationReportRepositorySupport @Inject()(configuration: Configuration) {
  lazy val notificationReportRepository: SentNotificationReportRepository =
    new DynamoNotificationReportRepository(
      AsyncDynamo(region = EU_WEST_1),
      configuration.dynamoReportsTableName
    )
}
