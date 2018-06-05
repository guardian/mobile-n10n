package com.gu.notificationschedule.notifications

import com.gu.notificationschedule.NotificationScheduleConfig
import com.gu.notificationschedule.cloudwatch.CloudWatchMetrics
import com.gu.notificationschedule.dynamo.NotificationsScheduleEntry
import okhttp3._
import org.apache.http.client.utils.URIBuilder
import org.apache.logging.log4j.{LogManager, Logger}

import scala.util.{Failure, Success, Try}

class RequestNotificationException(message: String) extends Exception(message)

trait RequestNotification {
  def apply(nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry): Try[Unit]
}

class RequestNotificationImpl(
                               config: NotificationScheduleConfig,
                               okHttpClient: OkHttpClient,
                               cloudWatchMetrics: CloudWatchMetrics
                             ) extends RequestNotification {
  private val logger: Logger = LogManager.getLogger(classOf[RequestNotificationImpl])
  private val url = new URIBuilder(config.pushTopicsUrl).addParameter("api-key", config.apiKey).build().toURL
  private val jsonMediaType = MediaType.parse("application/json; charset=utf-8")

  def apply(nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry): Try[Unit] = {
    cloudWatchMetrics.timeTry("notification-request", () =>
      tryRequestNotification(notificationsScheduleEntry) match {
        case Success(Some(response)) => {
          if (response.isSuccessful) {
            logger.info("Success: request: {}\n Got response {} ", notificationsScheduleEntry: Any, response: Any)
            Success(())
          }
          else {
            logger.warn("Unsuccessful response: request: {}\nGot response {} ", notificationsScheduleEntry: Any, response: Any)
            Failure(new RequestNotificationException(s"Unsuccessful response.\nRequest: $notificationsScheduleEntry\nResponse: $response"))
          }

        }
        case Success(None) => {
          logger.warn("No response: request: {}", notificationsScheduleEntry)
          Failure(new RequestNotificationException(s"Missing response.\nRequest: $notificationsScheduleEntry\n"))
        }
        case Failure(t) => {
          logger.warn(s"Failed request: $notificationsScheduleEntry", t)
          Failure(t)
        }
      }
    )

  }

  private def tryRequestNotification(notificationsScheduleEntry: NotificationsScheduleEntry): Try[Option[Response]] = Try {
    Option(okHttpClient.newCall(new Request.Builder()
      .url(url)
      .post(RequestBody.create(
        jsonMediaType,
        notificationsScheduleEntry.notification
      ))
      .build()).execute())
  }

}
