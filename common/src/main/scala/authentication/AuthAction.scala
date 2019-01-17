package authentication

import models.Topic
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

case class RequestWithAuthentication[A](isPermittedTopic: Topic => Boolean, request: Request[A]) extends WrappedRequest[A](request)

abstract class AuthAction(controllerComponents: ControllerComponents) extends ActionBuilder[RequestWithAuthentication, AnyContent] with Results {

  def validApiKey(key: String) : Boolean

  def isPermittedTopic(apiKey: String) : Topic => Boolean

  override def parser: BodyParser[AnyContent] = controllerComponents.parsers.defaultBodyParser

  override def executionContext: ExecutionContext = controllerComponents.executionContext

  override def invokeBlock[A](request: Request[A], block: RequestWithAuthentication[A] => Future[Result]): Future[Result] = {
    getApiKey(request) match {
      case Some(apiKey) if validApiKey(apiKey) => block(RequestWithAuthentication(isPermittedTopic(apiKey), request))
      case _ => Future.successful(Unauthorized("A valid api key is required"))
    }
  }

  private def getApiKey[A](request: Request[A]) = request.headers.get("Authorization").orElse(request.getQueryString("api-key"))
}

