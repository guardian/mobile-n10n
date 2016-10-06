package authentication

import models.Topic
import play.api.mvc._

import scala.concurrent.Future

trait AuthenticationSupport {

  def validApiKey(apiKey: String): Boolean

  def isPermittedTopic(apiKey: String): Topic => Boolean

  case class RequestWithAuthentication[A](isPermittedTopic: Topic => Boolean, request: Request[A]) extends WrappedRequest[A](request)

  object AuthenticatedAction extends ActionBuilder[RequestWithAuthentication] with Results {
    override def invokeBlock[A](request: Request[A], block: (RequestWithAuthentication[A]) => Future[Result]): Future[Result] = {
      request.getQueryString("api-key") match {
        case Some(apiKey) if validApiKey(apiKey) => block(RequestWithAuthentication(isPermittedTopic(apiKey), request))
        case _ => Future.successful(Unauthorized("A valid API key is required."))
      }
    }
  }
}
