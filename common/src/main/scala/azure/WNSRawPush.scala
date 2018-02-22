package azure
import play.api.http.Writeable
import play.api.libs.ws.{DefaultBodyWritables, WSRequest, WSResponse}
import utils.WriteableImplicits._

import scala.concurrent.Future

case class WNSRawPush(body: String, tags: Option[Tags]) extends RawPush with DefaultBodyWritables {
  override def format: String = "windows"

  override def extraHeaders: List[(String, String)] = List("X-WNS-Type" -> "wns/raw")

  override def post(request: WSRequest): Future[WSResponse] = request.post[String](body)
}