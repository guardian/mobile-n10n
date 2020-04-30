package notification.services

import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait FastlyPurge {
  def softPurge(contentApiId: String): Future[Boolean]
}

class FastlyPurgeImpl(wsClient: WSClient, configuration: Configuration)(implicit ec: ExecutionContext) extends FastlyPurge {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def softPurge(contentApiId: String): Future[Boolean] = {
    val url = s"${configuration.fastlyApiEndpoint}/service/${configuration.fastlyService}/purge/Item/$contentApiId"

    wsClient.url(url)
      .addHttpHeaders("Fastly-Soft-Purge" -> "1")
      .addHttpHeaders("Fastly-Key" -> s"${configuration.fastlyKey}")
      .withRequestTimeout(2.seconds)
      .execute("POST")
      .map { resp =>
        logger.info(s"Soft purged $url got HTTP ${resp.status} back ${resp.body}")
        if (resp.status == 200) {
          true
        } else {
          throw new Exception(s"Unable to soft purge url, got HTTP ${resp.status} for $url")
        }
      }
  }

}
