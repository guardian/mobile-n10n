package registration

import org.slf4j.{Logger, LoggerFactory}
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router
import play.core.SourceMapper

import scala.concurrent._

class CustomErrorHandler(env: Environment, config: Configuration, sourceMapper: Option[SourceMapper], router: => Option[Router]) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  override def onBadRequest(request: RequestHeader, message: String): Future[Result] = {
    val debugInfo = request.headers.get("User-Agent")
    logger.error(s"Bad request due to $message. User agent = $debugInfo")
    Future.successful(BadRequest("Bad request"))
  }
}
