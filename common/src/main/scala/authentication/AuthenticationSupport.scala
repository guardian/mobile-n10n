package authentication

import models.Topic
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationSupport {

  def validApiKey(apiKey: String): Boolean

  def isPermittedTopic(apiKey: String): Topic => Boolean

  case class RequestWithAuthentication[A](isPermittedTopic: Topic => Boolean, request: Request[A]) extends WrappedRequest[A](request)

  //make this a class and iNJECT IT
  object AuthenticatedAction extends ActionBuilder[RequestWithAuthentication, AnyContent] with Results {
    override def invokeBlock[A](request: Request[A], block: (RequestWithAuthentication[A]) => Future[Result]): Future[Result] = {
      request.getQueryString("api-key") match {
        case Some(apiKey) if validApiKey(apiKey) => block(RequestWithAuthentication(isPermittedTopic(apiKey), request))
        case _ => Future.successful(Unauthorized("A valid API key is required."))
      }
    }

    override def parser: BodyParser[AnyContent] = ???

    override protected def executionContext: ExecutionContext = ???
  }
}
