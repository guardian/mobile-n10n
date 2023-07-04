package com.gu.notificationschedule.notifications

import com.gu.notificationschedule.NotificationScheduleConfig
import com.gu.notificationschedule.cloudwatch.CloudWatchMetrics
import com.gu.notificationschedule.dynamo.NotificationsScheduleEntry
import okhttp3._
import org.apache.http.client.utils.URIBuilder
import org.slf4j.{Logger, LoggerFactory}

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
  private val logger: Logger = LoggerFactory.getLogger(classOf[RequestNotificationImpl])
  private val url = new URIBuilder(config.pushTopicsUrl).build().toURL
  private val jsonMediaType = MediaType.parse("application/json; charset=utf-8")
  private val authHeaderValue = s"Bearer ${config.apiKey}"

  def apply(nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry): Try[Unit] = {
    cloudWatchMetrics.timeTry("notification-request", () =>
      tryRequestNotification(notificationsScheduleEntry) match {
        case Success(Some(response)) => {
          try {
            if (response.isSuccessful) {
              logger.info("Success: request: {}\n Got response {} ", notificationsScheduleEntry: Any, response: Any)
              Success(())
            }
            else {
              logger.warn("Unsuccessful response: request: {}\nGot response {} ", notificationsScheduleEntry: Any, response: Any)
              Failure(new RequestNotificationException(s"Unsuccessful response.\nRequest: $notificationsScheduleEntry\nResponse: $response"))
            }
          }
          finally {
            Option(response.body).foreach(_.close)
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
      .header("Authorization", authHeaderValue)
      .post(RequestBody.create(
        notificationsScheduleEntry.notification,
        jsonMediaType
      ))
      .build()).execute())
  }

}
