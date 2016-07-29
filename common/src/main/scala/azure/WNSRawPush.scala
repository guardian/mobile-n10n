package azure
import play.api.http.Writeable
import utils.WriteableImplicits._

case class WNSRawPush(body: String, tags: Option[Tags])  extends RawPush[String] {
  override def format: String = "windows"

  override def writeable: Writeable[String] = implicitly[Writeable[String]].withContentType("application/octet-stream")

  override def extraHeaders: List[(String, String)] = List("X-WNS-Type" -> "wns/raw")
}