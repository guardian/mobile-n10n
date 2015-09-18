package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}
import services.NotificationConfiguration
import scala.concurrent.ExecutionContext

final class Main @Inject()(wsClient: WSClient,
  msNotificationsConfiguration: NotificationConfiguration)(
  implicit executionContext: ExecutionContext
  ) extends Controller {


  private val logger = Logger("main")

  def healthCheck = Action {
    Ok("Good")
  }
}