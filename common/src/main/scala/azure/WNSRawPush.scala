package azure
import play.api.http.Writeable
import play.api.libs.ws.{DefaultBodyWritables, WSRequest, WSResponse}
import utils.WriteableImplicits._

import scala.concurrent.Future

case class WNSRawPush(body: String, tags: Option[Tags]) extends RawPush[String] with DefaultBodyWritables {
  override def format: String = "windows"

  //private val writeable = implicitly[Writeable[String]].withContentType("application/octet-stream")

  override def extraHeaders: List[(String, String)] = List("X-WNS-Type" -> "wns/raw")

  override def post(request: WSRequest): Future[WSResponse] = request.post[String](body)
}