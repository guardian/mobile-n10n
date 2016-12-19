package registration.services

import auditor.ApiConfig
import pa.{PaClient, Response}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class WSPaClient(apiConfig: ApiConfig, wsClient: WSClient)(implicit ec: ExecutionContext) extends PaClient with pa.Http {
  override lazy val base = apiConfig.url
  override val apiKey = apiConfig.apiKey

  override def GET(urlString: String): Future[Response] = {
    wsClient.url(urlString).get().map { r =>
      Response(r.status, r.body, r.statusText)
    }
  }
}