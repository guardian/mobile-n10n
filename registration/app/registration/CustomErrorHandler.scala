package registration

import javax.inject._
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
    val debugInfo = request.headers.toSimpleMap
    logger.error(s"Bad request with headers $debugInfo due to $message")
    Future.successful(BadRequest("Bad request"))
  }
}
