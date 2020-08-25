package com.gu.mobile.notifications.football.lib

import com.gu.Logging
import com.gu.mobile.notifications.client.ApiClient
import com.gu.mobile.notifications.client.models.NotificationPayload

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class NotificationSender(notificationClient: ApiClient) extends Logging {

  def sendNotifications(notifications: List[NotificationPayload])(implicit ec: ExecutionContext): Future[Unit] = {
    Future.traverse(notifications)(sendNotification).map(_ => ())
  }

  def sendNotification(notification: NotificationPayload)(implicit ec: ExecutionContext): Future[Unit] = {
    val notificationString = notification.toString.replaceAll("\n", "")
    notificationClient.send(notification).map {
      case Right(_) => logger.info(s"Match status for $notificationString successfully sent")
      case Left(error) => logger.error(s"Error sending match status for $notificationString - ${error.description}")
    }.recover {
      case NonFatal(exception) => logger.error(s"Error sending match status for $notificationString ${exception.getMessage}", exception)
    }
  }
}
