package notification.controllers

import java.util.UUID
import javax.inject.Inject

import authentication.AuthenticationSupport
import error.NotificationsError
import models._
import notification.models.{Push, PushResult}
import notification.services.frontend.FrontendAlertsSupport
import notification.services.{Configuration, NotificationReportRepositorySupport, NotificationSenderSupport}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse.{json => BodyJson}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import tracking.{TrackingObserver, TrackingError}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.syntax.either._
import scalaz.{-\/, \/, \/-}

final class Main @Inject()(
  configuration: Configuration,
  notificationSenderSupport: NotificationSenderSupport,
  notificationReportRepositorySupport: NotificationReportRepositorySupport,
  frontendAlertsSupport: FrontendAlertsSupport)
  (implicit executionContext: ExecutionContext)
  extends Controller with AuthenticationSupport {

  val logger = Logger(classOf[Main])

  override def validApiKey(apiKey: String): Boolean = configuration.apiKey.contains(apiKey)

  import notificationReportRepositorySupport._
  import notificationSenderSupport._
  import frontendAlertsSupport._
  
  val trackingObservers = Seq(notificationReportRepository, frontendAlerts)
  
  def handleErrors[T](result: T): Result = result match {
    case error: NotificationsError => InternalServerError(error.reason)
  }

  def healthCheck: Action[AnyContent] = Action {
    Ok("Good")
  }

  @deprecated("A push notification can be sent to multiple topics, this is for backward compatibility only", since = "07/12/2015")
  def pushTopic(topic: Topic): Action[Notification] = pushTopics

  def pushTopics: Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val topics = request.body.topic
    val MaxTopics = 20
    topics.size match {
      case 0 => Future.successful(BadRequest("Empty topic list"))
      case a: Int if a > MaxTopics => Future.successful(BadRequest(s"Too many topics, maximum: $MaxTopics"))
      case _ => pushGeneric(Push(request.body, Left(topics)))
    }
  }

  def pushUser(userId: UUID): Action[Notification] = AuthenticatedAction.async(BodyJson[Notification]) { request =>
    val push = Push(request.body, Right(UserId(userId)))
    pushGeneric(push)
  }
  
  private def pushGeneric(push: Push) = {
    notificationSender.sendNotification(push) flatMap {
      case \/-(report) =>
        logger.info(s"Notification was sent: $push")
        notifyAboutPush(trackingObservers, report) map {
          case \/-(_) => Created(Json.toJson(PushResult.fromReport(report)))
          case -\/(errors) => Created(Json.toJson(PushResult.fromReport(report).withTrackingErrors(errors.map(_.reason))))
        }
      case -\/(error) =>
        logger.error(s"Notification ($push) could not be sent: $error")
        Future.successful(handleErrors(error))
    }
  }

  private def notifyAboutPush(observers: Seq[TrackingObserver], report: NotificationReport) = {
    val notifyObservers = observers.map { _.notificationSent(report) }
    val noErrors = ().right[List[TrackingError]]
    Future.fold(notifyObservers)(noErrors) {
      case (aggr, -\/(err)) => List(err).left
      case (-\/(errors), -\/(err)) => (err :: errors).left
      case (aggr, _) => aggr
    }
  }
}
