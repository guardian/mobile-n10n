package notification.services

import play.api.Logger
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

trait FastlyPurge {
  def softPurge(url: String): Future[Boolean]
}

class FastlyPurgeImpl(wsClient: WSClient)(implicit ec: ExecutionContext) extends FastlyPurge {

  private val logger: Logger = Logger(this.getClass)

  def softPurge(url: String): Future[Boolean] = {

    wsClient.url(url)
      .addHttpHeaders("Fastly-Soft-Purge" -> "1")
      .execute("PURGE")
      .map { resp =>
        logger.info(s"Soft purged $url got HTTP ${resp.status} back")
        if (resp.status == 200) {
          true
        } else {
          throw new Exception(s"Unable to soft purge url, got HTTP ${resp.status} for $url")
        }
      }
  }

}
