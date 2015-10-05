package authentication

import play.api.mvc.{Result, Results, Request, ActionBuilder}

import scala.concurrent.Future

trait AuthenticationSupport {

  def validApiKey(apiKey: String): Boolean

  def AuthenticatedAction: ActionBuilder[Request] = new ActionBuilder[Request] with Results {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      request.getQueryString("api-key") match {
        case Some(apiKey) if validApiKey(apiKey) => block(request)
        case _ => Future.successful(Unauthorized("A valid API key is required."))
      }
    }
  }
}
