package com.gu.mobile.notifications.football.lib

import com.gu.mobile.notifications.client.models.NotificationPayload
import com.gu.mobile.notifications.football.Logging

import scala.concurrent.{ExecutionContext, Future}

class NotificationSender(notificationClient: NotificationsApiClient) extends Logging {

  def sendNotifications(notifications: List[NotificationPayload])(implicit ec: ExecutionContext): Future[Unit] = {
    Future.traverse(notifications)(sendNotification).map(_ => ())
  }

  def sendNotification(notification: NotificationPayload)(implicit ec: ExecutionContext): Future[Unit] = {
    val notificationString = notification.toString.replaceAll("\n", "")
    notificationClient.send(notification) match {
      case Right(_) => Future.successful(logger.info(s"Match status for $notificationString successfully sent"))
      case Left(error) => Future.successful(logger.error(s"Error sending match status for $notificationString - ${error}"))
    }
  }
}
