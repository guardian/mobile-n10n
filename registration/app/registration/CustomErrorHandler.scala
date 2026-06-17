package registration

import org.slf4j.{Logger, LoggerFactory, MDC}
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
    try {
      Option(MDC.get("invalidTopics")) match {
        case Some(invalidTopics) =>
          val allTopics = Option(MDC.get("allTopics")).getOrElse("")
          logger.warn(s"Bad request: invalid topic type(s): [$invalidTopics]. All topics: [$allTopics]. User agent: ${debugInfo.getOrElse("unknown")}")
        case None =>
          logger.error(s"Bad request due to $message. User agent: ${debugInfo.getOrElse("unknown")}")
      }
    } finally {
      MDC.remove("invalidTopics")
      MDC.remove("allTopics")
    }
    Future.successful(BadRequest("Bad request"))
  }
}
