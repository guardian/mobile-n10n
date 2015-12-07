package notification.controllers

import java.util.UUID
import javax.inject.Inject

import authentication.AuthenticationSupport
import models._
import notification.models.Push
import notification.services.{NotificationSenderSupport, NotificationReportRepositorySupport, Configuration}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{AnyContent, Action, Controller, Result}
import providers.Error

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, \/-}

final class Main @Inject()(
  configuration: Configuration,
  notificationSenderSupport: NotificationSenderSupport,
  notificationReportRepositorySupport: NotificationReportRepositorySupport)
  (implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  val logger = Logger(classOf[Main])

  override def validApiKey(apiKey: String): Boolean = configuration.apiKey.contains(apiKey)

  import notificationReportRepositorySupport._
  import notificationSenderSupport._

  def handleErrors[T](result: T): Result = result match {
    case error: Error => InternalServerError(error.reason)
  }

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  private def pushGeneric(push: Push) = {
    notificationSender.sendNotification(push) flatMap {
      case \/-(report) =>
        notificationReportRepository.store(report) map {
          case \/-(_) =>
            logger.info(s"Notification was sent: $push")
            Created(Json.toJson(PushResult(push.notification.id)))
          case -\/(error) =>
            logger.error(s"Notification ($push) sent ($report) but report could not be stored ($error)")
            InternalServerError(s"Notification sent but report could not be stored ($error)")
        }

      case -\/(error) =>
        logger.error(s"Notification ($push) could not be sent: $error")
        Future.successful(handleErrors(error))
    }
  }

  def pushTopic(topic: Topic): Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Left(topic))
    pushGeneric(push)
  }

  def pushUser(userId: UUID): Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Right(UserId(userId)))
    pushGeneric(push)
  }

}
