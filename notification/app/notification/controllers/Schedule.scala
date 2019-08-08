package notification.controllers

import java.time.{Duration, ZonedDateTime}
import java.util.UUID

import authentication.AuthAction
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceAsync, NotificationsScheduleEntry}
import models.Notification
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, ControllerComponents}

import scala.concurrent.ExecutionContext

class Schedule(authAction: AuthAction, controllerComponents: ControllerComponents, notificationSchedulePersistence: NotificationSchedulePersistenceAsync)
              (implicit executionContext: ExecutionContext) extends AbstractController(controllerComponents) {
  def scheduleNotification(dateString: String): Action[Notification]  = authAction.async(parse.json[Notification]) { request =>
    val date = ZonedDateTime.parse(dateString)
    val notification = request.body
    val sevenDaysInSeconds = Duration.ofDays(7).getSeconds
    notificationSchedulePersistence.writeAsync(NotificationsScheduleEntry(
      UUID.randomUUID().toString,
      Json.prettyPrint(Json.toJson(notification)),
      date.toEpochSecond,
      date.toEpochSecond + sevenDaysInSeconds
    ), None).future.map( _ => {
      Ok
    })
  }
}
