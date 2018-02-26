package azure
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.libs.ws.DefaultBodyWritables._


import scala.concurrent.Future

case class WNSRawPush(body: String, tags: Option[Tags]) extends RawPush  {
  override def format: String = "windows"

  override def extraHeaders: List[(String, String)] = List("X-WNS-Type" -> "wns/raw")

  override def post(request: WSRequest): Future[WSResponse] = request.post[String](body)
}