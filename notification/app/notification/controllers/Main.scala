package notification.controllers

import java.util.UUID
import javax.inject.Inject

import authentication.AuthenticationSupport
import models.{Notification, Push, Topic, UserId}
import notification.services.{NotificationSenderSupport, NotificationReportRepositorySupport, Configuration}
import play.Logger
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, Controller, Result}
import providers.Error

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/-}

final class Main @Inject()(
  configuration: Configuration,
  notificationSenderSupport: NotificationSenderSupport,
  notificationReportRepositorySupport: NotificationReportRepositorySupport)
  (implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  override def validApiKey(apiKey: String) = configuration.apiKey.contains(apiKey)

  import notificationReportRepositorySupport._
  import notificationSenderSupport._

  def handleErrors[T](result: T): Result = result match {
    case error: Error => InternalServerError(error.reason)
  }

  def healthCheck = Action {
    Ok("Good")
  }

  private def pushGeneric(push: Push) = {
    notificationSender.sendNotification(push) flatMap {
      case \/-(report) =>
        notificationReportRepository.store(report) map {
          case \/-(_) =>
            Ok("Ok")
          case -\/(error) =>
            Logger.error(s"Notification sent ($report) but not report could not be stored ($error)")
            InternalServerError(s"Notification sent but report could not be stored ($error)")
        }

      case -\/(error) =>
        Future.successful(handleErrors(error))
    }
  }

  def pushTopic(topic: Topic) = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Left(topic))
    pushGeneric(push)
  }

  def pushUser(userId: UUID) = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Right(UserId(userId)))
    pushGeneric(push)
  }

}